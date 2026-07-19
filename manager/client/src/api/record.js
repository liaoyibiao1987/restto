import request from './request';

/**
 * 分页查询备份记录。
 *
 * @param {number} [taskId] 任务 ID
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @returns {Promise<object>} 分页结果
 */
export function fetchRecords(taskId, page = 1, size = 20) {
  const params = { page, size };
  if (taskId) {
    params.taskId = taskId;
  }
  return request.get('/records', { params });
}
