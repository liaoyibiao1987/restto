package com.restto.manager.controller;

import com.restto.manager.common.PageResult;
import com.restto.manager.common.Result;
import com.restto.manager.entity.BackupRecord;
import com.restto.manager.security.RequirePermission;
import com.restto.manager.service.BackupRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 备份执行记录接口。
 */
@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@RequirePermission("backup:record:list")
public class BackupRecordController {

    private final BackupRecordService backupRecordService;

    /**
     * 分页查询记录（可按任务过滤）。
     *
     * @param taskId 任务 ID（可选）
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<BackupRecord>> page(@RequestParam(required = false) Long taskId,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        return Result.success(backupRecordService.page(taskId, page, size));
    }
}
