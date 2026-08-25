import request from './request';

/**
 * 分页查询权限点。
 *
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @param {string} [module] 模块
 * @returns {Promise<object>} 分页结果
 */
export function fetchPermissions(page = 1, size = 20, module) {
  return request.get('/permissions', { params: { page, size, module } });
}

/**
 * 权限点详情。
 *
 * @param {number} id 权限 ID
 * @returns {Promise<object>} 权限点
 */
export function fetchPermission(id) {
  return request.get(`/permissions/${id}`);
}
