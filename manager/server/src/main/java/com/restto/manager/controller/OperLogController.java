package com.restto.manager.controller;

import com.restto.manager.common.PageResult;
import com.restto.manager.common.Result;
import com.restto.manager.entity.SysOperLog;
import com.restto.manager.security.OperLog;
import com.restto.manager.security.RequirePermission;
import com.restto.manager.service.OperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口。
 */
@RestController
@RequestMapping("/api/oper-logs")
@RequiredArgsConstructor
@RequirePermission("system:log:list")
public class OperLogController {

    private final OperLogService operLogService;

    /**
     * 分页查询操作日志。
     */
    @GetMapping
    public Result<PageResult<SysOperLog>> page(@RequestParam(required = false) String title,
                                               @RequestParam(required = false) String operUser,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        return Result.success(operLogService.page(title, operUser, page, size));
    }

    /**
     * 日志详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("system:log:query")
    public Result<SysOperLog> get(@PathVariable Long id) {
        return Result.success(operLogService.getById(id));
    }

    /**
     * 删除单条日志。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("system:log:delete")
    @OperLog("删除操作日志")
    public Result<Void> delete(@PathVariable Long id) {
        operLogService.removeById(id);
        return Result.success();
    }

    /**
     * 清空全部日志。
     */
    @DeleteMapping
    @RequirePermission("system:log:delete")
    @OperLog("清空操作日志")
    public Result<Void> clear() {
        operLogService.clearAll();
        return Result.success();
    }
}
