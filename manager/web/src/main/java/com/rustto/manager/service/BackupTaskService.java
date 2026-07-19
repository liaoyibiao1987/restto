package com.rustto.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rustto.manager.common.PageResult;
import com.rustto.manager.dto.TaskSaveRequest;
import com.rustto.manager.entity.BackupTask;

import java.util.List;

/**
 * 备份任务服务。
 */
public interface BackupTaskService extends IService<BackupTask> {

    /**
     * 创建任务。
     *
     * @param request 任务请求
     * @return 任务
     */
    BackupTask create(TaskSaveRequest request);

    /**
     * 更新任务。
     *
     * @param id      任务 ID
     * @param request 任务请求
     * @return 任务
     */
    BackupTask updateTask(Long id, TaskSaveRequest request);

    /**
     * 立即触发一次任务（经 Netty 下发）。
     *
     * @param id 任务 ID
     * @return 是否成功下发到在线节点
     */
    boolean runNow(Long id);

    /**
     * 分页查询。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<BackupTask> page(long page, long size);

    /**
     * 取所有启用的任务（供调度器使用）。
     *
     * @return 启用任务列表
     */
    List<BackupTask> listEnabled();
}
