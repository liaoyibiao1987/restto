import request from './request';

/**
 * 分页查询任务。
 *
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @returns {Promise<object>} 分页结果
 */
export function fetchTasks(page = 1, size = 20) {
  return request.get('/tasks', { params: { page, size } });
}

/**
 * 创建任务。
 *
 * @param {object} data 任务数据
 * @returns {Promise<object>} 任务
 */
export function createTask(data) {
  return request.post('/tasks', data);
}

/**
 * 更新任务。
 *
 * @param {number} id 任务 ID
 * @param {object} data 任务数据
 * @returns {Promise<object>} 任务
 */
export function updateTask(id, data) {
  return request.put(`/tasks/${id}`, data);
}

/**
 * 删除任务。
 *
 * @param {number} id 任务 ID
 * @returns {Promise<object>} 结果
 */
export function deleteTask(id) {
  return request.delete(`/tasks/${id}`);
}

/**
 * 立即触发任务。
 *
 * @param {number} id 任务 ID
 * @returns {Promise<boolean>} 是否下发成功
 */
export function runTask(id) {
  return request.post(`/tasks/${id}/run`);
}
