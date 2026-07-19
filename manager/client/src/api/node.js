import request from './request';

/**
 * 创建节点（返回一次性 Token）。
 *
 * @param {string} nodeName 节点名
 * @returns {Promise<object>} 节点
 */
export function createNode(nodeName) {
  return request.post('/nodes', { nodeName });
}

/**
 * 分页查询节点。
 *
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @returns {Promise<object>} 分页结果
 */
export function fetchNodes(page = 1, size = 20) {
  return request.get('/nodes', { params: { page, size } });
}

/**
 * 删除节点。
 *
 * @param {number} id 节点 ID
 * @returns {Promise<object>} 结果
 */
export function deleteNode(id) {
  return request.delete(`/nodes/${id}`);
}
