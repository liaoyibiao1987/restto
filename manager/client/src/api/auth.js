import request from './request';

/**
 * 登录。
 *
 * @param {string} username 用户名
 * @param {string} password 密码
 * @returns {Promise<{token:string,username:string,roles:string[],permissions:string[]}>} 登录响应
 */
export function login(username, password) {
  return request.post('/auth/login', { username, password });
}

/**
 * 当前登录用户信息（角色 + 权限码）。
 *
 * @returns {Promise<object>} 用户信息
 */
export function fetchInfo() {
  return request.get('/auth/info');
}

/**
 * 当前登录用户可见的菜单树。
 *
 * @returns {Promise<Array>} 菜单树
 */
export function fetchMenus() {
  return request.get('/auth/menus');
}
