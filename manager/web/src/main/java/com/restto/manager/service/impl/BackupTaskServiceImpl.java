package com.restto.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.restto.manager.common.BusinessException;
import com.restto.manager.common.PageResult;
import com.restto.manager.common.ResultCode;
import com.restto.manager.dto.TaskSaveRequest;
import com.restto.manager.entity.BackupTask;
import com.restto.manager.mapper.BackupTaskMapper;
import com.restto.manager.netty.ConnectionRegistry;
import com.restto.manager.netty.MessageType;
import com.restto.manager.netty.ProtocolCodec;
import com.restto.manager.netty.message.ProtocolMessages.TaskCommand;
import com.restto.manager.service.BackupRecordService;
import com.restto.manager.service.BackupTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 备份任务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupTaskServiceImpl extends ServiceImpl<BackupTaskMapper, BackupTask>
        implements BackupTaskService {

    private final ConnectionRegistry connectionRegistry;

    private final BackupRecordService backupRecordService;

    @Override
    public BackupTask create(TaskSaveRequest request) {
        BackupTask task = new BackupTask();
        applyRequest(task, request);
        task.setEnabled(Boolean.TRUE);
        save(task);
        return task;
    }

    @Override
    public BackupTask updateTask(Long id, TaskSaveRequest request) {
        BackupTask task = getById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        applyRequest(task, request);
        updateById(task);
        return task;
    }

    @Override
    public boolean runNow(Long id) {
        BackupTask task = getById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        if (!connectionRegistry.isOnline(task.getNodeId())) {
            log.warn("task {} target node {} offline", id, task.getNodeId());
            return false;
        }
        backupRecordService.createPending(id, task.getNodeId());
        TaskCommand command = new TaskCommand(id, task.getModule(), parseArgs(task.getParamsJson()));
        return connectionRegistry.send(task.getNodeId(), MessageType.TASK_COMMAND, command);
    }

    @Override
    public PageResult<BackupTask> page(long page, long size) {
        Page<BackupTask> result = page(new Page<>(page, size),
                new QueryWrapper<BackupTask>().orderByDesc("created_at"));
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }

    @Override
    public List<BackupTask> listEnabled() {
        return list(new QueryWrapper<BackupTask>().eq("enabled", 1));
    }

    private void applyRequest(BackupTask task, TaskSaveRequest request) {
        task.setName(request.getName());
        task.setNodeId(request.getNodeId());
        task.setModule(request.getModule());
        task.setParamsJson(request.getParamsJson());
        task.setCronExpr(request.getCronExpr());
        if (request.getEnabled() != null) {
            task.setEnabled(request.getEnabled());
        }
    }

    private JsonNode parseArgs(String paramsJson) {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return ProtocolCodec.MAPPER.nullNode();
        }
        try {
            return ProtocolCodec.MAPPER.readTree(paramsJson);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "paramsJson 不是合法 JSON");
        }
    }
}
