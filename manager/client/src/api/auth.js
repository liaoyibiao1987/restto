import request from './request';

/**
 * 登录。
 *
 * @param {string} username 用户名
 * @param {string} password 密码
 * @returns {Promise<{token:string,username:string,role:string}>} 登录响应
 */
export function login(username, password) {
  return request.post('/auth/login', { username, password });
}

/**
 * 当前登录用户信息。
 *
 * @returns {Promise<object>} 用户信息
 */
export function fetchUserInfo() {
  return request.get('/auth/info');
}
