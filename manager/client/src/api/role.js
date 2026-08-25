import request from './request';

/**
 * 分页查询角色。
 *
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @param {string} [roleName] 角色名片段
 * @returns {Promise<object>} 分页结果
 */
export function fetchRoles(page = 1, size = 20, roleName) {
  return request.get('/roles', { params: { page, size, roleName } });
}

/**
 * 全部角色（下拉用）。
 *
 * @returns {Promise<Array>} 角色列表
 */
export function fetchAllRoles() {
  return request.get('/roles/all');
}

/**
 * 角色详情。
 *
 * @param {number} id 角色 ID
 * @returns {Promise<object>} 角色
 */
export function fetchRole(id) {
  return request.get(`/roles/${id}`);
}

/**
 * 新建角色。
 *
 * @param {object} data 角色数据
 * @returns {Promise<object>} 角色
 */
export function createRole(data) {
  return request.post('/roles', data);
}

/**
 * 修改角色。
 *
 * @param {number} id 角色 ID
 * @param {object} data 修改数据
 * @returns {Promise<object>} 结果
 */
export function updateRole(id, data) {
  return request.put(`/roles/${id}`, data);
}

/**
 * 删除角色。
 *
 * @param {number} id 角色 ID
 * @returns {Promise<object>} 结果
 */
export function deleteRole(id) {
  return request.delete(`/roles/${id}`);
}

/**
 * 取角色已分配菜单 ID。
 *
 * @param {number} id 角色 ID
 * @returns {Promise<number[]>} 菜单 ID 列表
 */
export function fetchRoleMenus(id) {
  return request.get(`/roles/${id}/menus`);
}

/**
 * 给角色分配菜单。
 *
 * @param {number} id 角色 ID
 * @param {number[]} menuIds 菜单 ID
 * @returns {Promise<object>} 结果
 */
export function assignRoleMenus(id, menuIds) {
  return request.put(`/roles/${id}/menus`, { menuIds });
}

/**
 * 取角色已分配权限 ID。
 *
 * @param {number} id 角色 ID
 * @returns {Promise<number[]>} 权限 ID 列表
 */
export function fetchRolePermissions(id) {
  return request.get(`/roles/${id}/permissions`);
}

/**
 * 给角色分配权限。
 *
 * @param {number} id 角色 ID
 * @param {number[]} permissionIds 权限 ID
 * @returns {Promise<object>} 结果
 */
export function assignRolePermissions(id, permissionIds) {
  return request.put(`/roles/${id}/permissions`, { permissionIds });
}
