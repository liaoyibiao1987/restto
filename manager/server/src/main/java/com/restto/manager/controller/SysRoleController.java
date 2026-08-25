package com.restto.manager.controller;

import com.restto.manager.common.PageResult;
import com.restto.manager.common.Result;
import com.restto.manager.dto.AssignMenusRequest;
import com.restto.manager.dto.AssignPermissionsRequest;
import com.restto.manager.dto.SysRoleCreateRequest;
import com.restto.manager.dto.SysRoleUpdateRequest;
import com.restto.manager.entity.SysRole;
import com.restto.manager.security.OperLog;
import com.restto.manager.security.RequirePermission;
import com.restto.manager.service.SysRoleService;
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
import java.util.List;

/**
 * 角色接口。
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@RequirePermission("system:role:list")
public class SysRoleController {

    private final SysRoleService sysRoleService;

    /**
     * 分页查询角色。
     */
    @GetMapping
    public Result<PageResult<SysRole>> page(@RequestParam(required = false) String roleName,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size) {
        return Result.success(sysRoleService.page(roleName, page, size));
    }

    /**
     * 全部角色（下拉用，不分页）。
     */
    @GetMapping("/all")
    @RequirePermission("system:role:query")
    public Result<List<SysRole>> all() {
        return Result.success(sysRoleService.list());
    }

    /**
     * 角色详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("system:role:query")
    public Result<SysRole> get(@PathVariable Long id) {
        return Result.success(sysRoleService.getById(id));
    }

    /**
     * 新建角色。
     */
    @PostMapping
    @RequirePermission("system:role:create")
    @OperLog("新增角色")
    public Result<SysRole> create(@RequestBody @Valid SysRoleCreateRequest request) {
        return Result.success(sysRoleService.create(request));
    }

    /**
     * 修改角色。
     */
    @PutMapping("/{id}")
    @RequirePermission("system:role:update")
    @OperLog("修改角色")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid SysRoleUpdateRequest request) {
        sysRoleService.update(id, request);
        return Result.success();
    }

    /**
     * 删除角色。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("system:role:delete")
    @OperLog("删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.remove(id);
        return Result.success();
    }

    /**
     * 取角色已分配菜单 ID。
     */
    @GetMapping("/{id}/menus")
    @RequirePermission("system:role:assign-menus")
    public Result<List<Long>> menuIds(@PathVariable Long id) {
        return Result.success(sysRoleService.getMenuIds(id));
    }

    /**
     * 给角色分配菜单。
     */
    @PutMapping("/{id}/menus")
    @RequirePermission("system:role:assign-menus")
    @OperLog("分配角色菜单")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody @Valid AssignMenusRequest request) {
        sysRoleService.assignMenus(id, request.getMenuIds());
        return Result.success();
    }

    /**
     * 取角色已分配权限 ID。
     */
    @GetMapping("/{id}/permissions")
    @RequirePermission("system:role:assign-perms")
    public Result<List<Long>> permissionIds(@PathVariable Long id) {
        return Result.success(sysRoleService.getPermissionIds(id));
    }

    /**
     * 给角色分配权限。
     */
    @PutMapping("/{id}/permissions")
    @RequirePermission("system:role:assign-perms")
    @OperLog("分配角色权限")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody @Valid AssignPermissionsRequest request) {
        sysRoleService.assignPermissions(id, request.getPermissionIds());
        return Result.success();
    }
}
