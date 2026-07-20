import request from './request';

/**
 * 分页查询操作日志。
 *
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @param {string} [title] 标题片段
 * @param {string} [operUser] 操作人
 * @returns {Promise<object>} 分页结果
 */
export function fetchLogs(page = 1, size = 20, title, operUser) {
  return request.get('/oper-logs', { params: { page, size, title, operUser } });
}

/**
 * 日志详情。
 *
 * @param {number} id 日志 ID
 * @returns {Promise<object>} 日志
 */
export function fetchLog(id) {
  return request.get(`/oper-logs/${id}`);
}

/**
 * 删除单条日志。
 *
 * @param {number} id 日志 ID
 * @returns {Promise<object>} 结果
 */
export function deleteLog(id) {
  return request.delete(`/oper-logs/${id}`);
}

/**
 * 清空全部日志。
 *
 * @returns {Promise<object>} 结果
 */
export function clearLogs() {
  return request.delete('/oper-logs');
}
