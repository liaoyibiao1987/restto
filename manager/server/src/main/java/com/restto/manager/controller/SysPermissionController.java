package com.restto.manager.controller;

import com.restto.manager.common.PageResult;
import com.restto.manager.common.Result;
import com.restto.manager.entity.system.permission.SysPermission;
import com.restto.manager.security.RequirePermission;
import com.restto.manager.service.system.permission.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限点接口（V2 为种子驱动，仅提供查询；增删改预留）。
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@RequirePermission("system:permission:list")
public class SysPermissionController {

    private final SysPermissionService sysPermissionService;

    /**
     * 分页查询权限点。
     */
    @GetMapping
    public Result<PageResult<SysPermission>> page(@RequestParam(required = false) String module,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "20") long size) {
        return Result.success(sysPermissionService.page(module, page, size));
    }

    /**
     * 权限点详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("system:permission:query")
    public Result<SysPermission> get(@PathVariable Long id) {
        return Result.success(sysPermissionService.getById(id));
    }
}
