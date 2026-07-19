import request from './request';

/**
 * 上传客户端二进制。
 *
 * @param {File} file 文件
 * @param {string} version 版本号
 * @returns {Promise<object>} 二进制记录
 */
export function uploadBinary(file, version) {
  const form = new FormData();
  form.append('file', file);
  form.append('version', version);
  return request.post('/binaries/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/**
 * 分页查询二进制版本。
 *
 * @param {number} page 页码
 * @param {number} size 每页大小
 * @returns {Promise<object>} 分页结果
 */
export function fetchBinaries(page = 1, size = 20) {
  return request.get('/binaries', { params: { page, size } });
}

/**
 * 下发二进制到节点。
 *
 * @param {number} id 二进制 ID
 * @param {number} nodeId 节点 ID
 * @returns {Promise<boolean>} 是否下发成功
 */
export function pushBinary(id, nodeId) {
  return request.post(`/binaries/${id}/push`, { nodeId });
}
