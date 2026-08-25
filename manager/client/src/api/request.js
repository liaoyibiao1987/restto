import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../stores/auth';
import router from '../router';

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000,
});

/**
 * 请求拦截器：注入 Authorization: Bearer <token>。
 */
request.interceptors.request.use((config) => {
  const auth = useAuthStore();
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`;
  }
  return config;
});

/**
 * 响应拦截器：业务码非 0 视为失败；401 跳登录；403/业务 40300 仅提示无权限。
 */
request.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && typeof body.code !== 'undefined' && body.code !== 0) {
      if (body.code === 40300) {
        ElMessage.warning(body.message || '无权限');
      } else {
        ElMessage.error(body.message || '请求失败');
      }
      return Promise.reject(new Error(body.message || '请求失败'));
    }
    // 自动解包 Result.data，调用方直接拿到业务 payload
    return body ? body.data : body;
  },
  (error) => {
    const status = error.response ? error.response.status : 0;
    const bodyCode = error.response && error.response.data && error.response.data.code;
    if (status === 401) {
      useAuthStore().logout();
      router.push('/login');
    } else if (status === 403 || bodyCode === 40300) {
      ElMessage.warning('无权限执行该操作');
    } else {
      const message = (error.response && error.response.data && error.response.data.message)
        || error.message
        || '网络错误';
      ElMessage.error(message);
    }
    return Promise.reject(error);
  },
);

export default request;
