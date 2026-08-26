package com.restto.manager.controller;

import com.restto.manager.common.BusinessException;
import com.restto.manager.common.PageResult;
import com.restto.manager.common.Result;
import com.restto.manager.common.ResultCode;
import com.restto.manager.dto.backup.task.TaskSaveRequest;
import com.restto.manager.entity.backup.task.BackupTask;
import com.restto.manager.security.OperLog;
import com.restto.manager.security.RequirePermission;
import com.restto.manager.service.backup.task.BackupTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 备份任务接口。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@RequirePermission("backup:task:list")
public class BackupTaskController {

    private final BackupTaskService backupTaskService;

    /**
     * 创建任务。
     *
     * @param request 任务请求
     * @return 任务
     */
    @PostMapping
    @RequirePermission("backup:task:create")
    @OperLog("新增备份任务")
    public Result<BackupTask> create(@RequestBody @Valid TaskSaveRequest request) {
        return Result.success(backupTaskService.create(request));
    }

    /**
     * 更新任务。
     *
     * @param id      任务 ID
     * @param request 任务请求
     * @return 任务
     */
    @PutMapping("/{id}")
    @RequirePermission("backup:task:update")
    @OperLog("修改备份任务")
    public Result<BackupTask> update(@PathVariable Long id, @RequestBody @Valid TaskSaveRequest request) {
        return Result.success(backupTaskService.updateTask(id, request));
    }

    /**
     * 分页查询任务。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<BackupTask>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        return Result.success(backupTaskService.page(page, size));
    }

    /**
     * 任务详情。
     *
     * @param id 任务 ID
     * @return 任务
     */
    @GetMapping("/{id}")
    @RequirePermission("backup:task:query")
    public Result<BackupTask> get(@PathVariable Long id) {
        BackupTask task = backupTaskService.getById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 删除任务。
     *
     * @param id 任务 ID
     * @return 结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("backup:task:delete")
    @OperLog("删除备份任务")
    public Result<Void> delete(@PathVariable Long id) {
        backupTaskService.removeById(id);
        return Result.success();
    }

    /**
     * 立即触发一次任务。
     *
     * @param id 任务 ID
     * @return 是否成功下发到在线节点
     */
    @PostMapping("/{id}/run")
    @RequirePermission("backup:task:run")
    @OperLog("立即执行备份任务")
    public Result<Boolean> run(@PathVariable Long id) {
        return Result.success(backupTaskService.runNow(id));
    }
}
