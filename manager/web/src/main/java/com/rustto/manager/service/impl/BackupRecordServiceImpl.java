package com.rustto.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rustto.manager.common.PageResult;
import com.rustto.manager.entity.BackupRecord;
import com.rustto.manager.mapper.BackupRecordMapper;
import com.rustto.manager.netty.message.ProtocolMessages.TaskResult;
import com.rustto.manager.service.BackupRecordService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 备份记录服务实现。
 */
@Service
public class BackupRecordServiceImpl extends ServiceImpl<BackupRecordMapper, BackupRecord>
        implements BackupRecordService {

    @Override
    public BackupRecord createPending(Long taskId, Long nodeId) {
        BackupRecord record = new BackupRecord();
        record.setTaskId(taskId);
        record.setNodeId(nodeId);
        record.setStatus("pending");
        record.setStartAt(LocalDateTime.now());
        save(record);
        return record;
    }

    @Override
    public void applyResult(Long taskId, Long nodeId, TaskResult result) {
        BackupRecord record = getOne(new QueryWrapper<BackupRecord>()
                .eq("task_id", taskId)
                .eq("status", "pending")
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (record == null) {
            record = new BackupRecord();
            record.setTaskId(taskId);
            record.setNodeId(nodeId);
            record.setStartAt(LocalDateTime.now());
        }
        record.setNodeId(nodeId);
        record.setStatus(result.getStatus());
        record.setFilePath(result.getFilePath());
        record.setSize(result.getSize());
        record.setChecksum(result.getChecksum());
        record.setErrorMsg(result.getError());
        record.setEndAt(LocalDateTime.now());
        saveOrUpdate(record);
    }

    @Override
    public PageResult<BackupRecord> page(Long taskId, long page, long size) {
        QueryWrapper<BackupRecord> qw = new QueryWrapper<BackupRecord>().orderByDesc("id");
        if (taskId != null) {
            qw.eq("task_id", taskId);
        }
        Page<BackupRecord> result = page(new Page<>(page, size), qw);
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }
}
