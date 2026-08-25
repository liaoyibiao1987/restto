import request from './request';

/**
 * 分页查询用户。
 *
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @param {string} [username] 用户名片段
 * @param {number} [status] 状态
 * @returns {Promise<object>} 分页结果
 */
export function fetchUsers(page = 1, size = 20, username, status) {
  return request.get('/users', { params: { page, size, username, status } });
}

/**
 * 用户详情。
 *
 * @param {number} id 用户 ID
 * @returns {Promise<object>} 用户视图
 */
export function fetchUser(id) {
  return request.get(`/users/${id}`);
}

/**
 * 新建用户。
 *
 * @param {object} data 用户数据
 * @returns {Promise<object>} 用户视图
 */
export function createUser(data) {
  return request.post('/users', data);
}

/**
 * 修改用户。
 *
 * @param {number} id 用户 ID
 * @param {object} data 修改数据
 * @returns {Promise<object>} 结果
 */
export function updateUser(id, data) {
  return request.put(`/users/${id}`, data);
}

/**
 * 删除用户。
 *
 * @param {number} id 用户 ID
 * @returns {Promise<object>} 结果
 */
export function deleteUser(id) {
  return request.delete(`/users/${id}`);
}

/**
 * 给用户分配角色。
 *
 * @param {number} id 用户 ID
 * @param {number[]} roleIds 角色 ID
 * @returns {Promise<object>} 结果
 */
export function assignUserRoles(id, roleIds) {
  return request.put(`/users/${id}/roles`, { roleIds });
}

/**
 * 重置用户密码。
 *
 * @param {number} id 用户 ID
 * @param {string} newPassword 新密码
 * @returns {Promise<object>} 结果
 */
export function resetUserPassword(id, newPassword) {
  return request.put(`/users/${id}/password`, { newPassword });
}
