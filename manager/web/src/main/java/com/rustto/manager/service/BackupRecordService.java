package com.rustto.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rustto.manager.common.PageResult;
import com.rustto.manager.entity.BackupRecord;
import com.rustto.manager.netty.message.ProtocolMessages.TaskResult;

/**
 * 备份记录服务。
 */
public interface BackupRecordService extends IService<BackupRecord> {

    /**
     * 任务下发时创建一条 pending 记录。
     *
     * @param taskId 任务 ID
     * @param nodeId 节点 ID
     * @return 记录
     */
    BackupRecord createPending(Long taskId, Long nodeId);

    /**
     * 应用客户端回传的执行结果：补全最近一条 pending 记录，缺失则新建。
     *
     * @param taskId 任务 ID
     * @param nodeId 节点 ID
     * @param result 客户端回传结果
     */
    void applyResult(Long taskId, Long nodeId, TaskResult result);

    /**
     * 分页查询（可按任务过滤）。
     *
     * @param taskId 任务 ID（可空）
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    PageResult<BackupRecord> page(Long taskId, long page, long size);
}
