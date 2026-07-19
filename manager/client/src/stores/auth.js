import { defineStore } from 'pinia';

/**
 * 鉴权状态：token / 用户名，持久化到 localStorage。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    username: localStorage.getItem('username') || '',
  }),
  actions: {
    /**
     * 保存登录态。
     *
     * @param {string} token JWT
     * @param {string} username 用户名
     */
    setAuth(token, username) {
      this.token = token;
      this.username = username;
      localStorage.setItem('token', token);
      localStorage.setItem('username', username);
    },
    /**
     * 清除登录态。
     */
    logout() {
      this.token = '';
      this.username = '';
      localStorage.removeItem('token');
      localStorage.removeItem('username');
    },
  },
});
