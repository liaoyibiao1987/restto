import request from './request';

/**
 * 全量菜单树。
 *
 * @returns {Promise<Array>} 菜单树
 */
export function fetchMenuTree() {
  return request.get('/menus');
}

/**
 * 菜单详情。
 *
 * @param {number} id 菜单 ID
 * @returns {Promise<object>} 菜单
 */
export function fetchMenu(id) {
  return request.get(`/menus/${id}`);
}

/**
 * 新建菜单。
 *
 * @param {object} data 菜单数据
 * @returns {Promise<object>} 菜单
 */
export function createMenu(data) {
  return request.post('/menus', data);
}

/**
 * 修改菜单。
 *
 * @param {number} id 菜单 ID
 * @param {object} data 菜单数据
 * @returns {Promise<object>} 结果
 */
export function updateMenu(id, data) {
  return request.put(`/menus/${id}`, data);
}

/**
 * 删除菜单。
 *
 * @param {number} id 菜单 ID
 * @returns {Promise<object>} 结果
 */
export function deleteMenu(id) {
  return request.delete(`/menus/${id}`);
}
