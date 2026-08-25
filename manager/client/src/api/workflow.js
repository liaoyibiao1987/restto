import request from './request';

/**
 * 分页查询工作流。
 *
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @returns {Promise<object>} 分页结果
 */
export function fetchWorkflows(page = 1, size = 20) {
  return request.get('/workflows', { params: { page, size } });
}

/**
 * 工作流详情（含整图）。
 *
 * @param {number} id 工作流 ID
 * @returns {Promise<object>} 工作流详情
 */
export function getWorkflow(id) {
  return request.get(`/workflows/${id}`);
}

/**
 * 创建工作流（含整图）。
 *
 * @param {object} data 工作流数据
 * @returns {Promise<object>} 工作流详情
 */
export function createWorkflow(data) {
  return request.post('/workflows', data);
}

/**
 * 更新工作流（整图替换）。
 *
 * @param {number} id 工作流 ID
 * @param {object} data 工作流数据
 * @returns {Promise<object>} 工作流详情
 */
export function updateWorkflow(id, data) {
  return request.put(`/workflows/${id}`, data);
}

/**
 * 删除工作流。
 *
 * @param {number} id 工作流 ID
 * @returns {Promise<object>} 结果
 */
export function deleteWorkflow(id) {
  return request.delete(`/workflows/${id}`);
}

/**
 * 立即运行一次工作流。
 *
 * @param {number} id 工作流 ID
 * @returns {Promise<number>} 执行 ID
 */
export function runWorkflow(id) {
  return request.post(`/workflows/${id}/run`);
}

/**
 * 分页查询执行历史。
 *
 * @param {number} [workflowId] 工作流 ID（可空）
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @returns {Promise<object>} 分页结果
 */
export function fetchExecutions(workflowId, page = 1, size = 20) {
  const params = { page, size };
  if (workflowId) params.workflowId = workflowId;
  return request.get('/workflow-executions', { params });
}

/**
 * 执行详情（含每步节点结果）。
 *
 * @param {number} id 执行 ID
 * @returns {Promise<object>} 执行详情
 */
export function getExecution(id) {
  return request.get(`/workflow-executions/${id}`);
}
