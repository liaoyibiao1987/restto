package com.rustto.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rustto.manager.common.PageResult;
import com.rustto.manager.entity.SysOperLog;

/**
 * 操作日志服务。
 */
public interface OperLogService extends IService<SysOperLog> {

    /**
     * 分页查询操作日志（可按标题、操作人过滤）。
     *
     * @param title    标题片段（可空）
     * @param operUser 操作人（可空）
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    PageResult<SysOperLog> page(String title, String operUser, long page, long size);

    /**
     * 清空全部操作日志。
     */
    void clearAll();
}
