package com.restto.manager.controller;

import com.restto.manager.common.PageResult;
import com.restto.manager.common.Result;
import com.restto.manager.dto.AssignRolesRequest;
import com.restto.manager.dto.ResetPasswordRequest;
import com.restto.manager.dto.SysUserCreateRequest;
import com.restto.manager.dto.SysUserUpdateRequest;
import com.restto.manager.dto.SysUserView;
import com.restto.manager.security.OperLog;
import com.restto.manager.security.RequirePermission;
import com.restto.manager.service.SysUserService;
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
 * 后台用户接口。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@RequirePermission("system:user:list")
public class SysUserController {

    private final SysUserService sysUserService;

    /**
     * 分页查询用户。
     */
    @GetMapping
    public Result<PageResult<SysUserView>> page(@RequestParam(required = false) String username,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size) {
        return Result.success(sysUserService.page(username, status, page, size));
    }

    /**
     * 用户详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("system:user:query")
    public Result<SysUserView> get(@PathVariable Long id) {
        return Result.success(sysUserService.view(id));
    }

    /**
     * 新建用户。
     */
    @PostMapping
    @RequirePermission("system:user:create")
    @OperLog("新增用户")
    public Result<SysUserView> create(@RequestBody @Valid SysUserCreateRequest request) {
        return Result.success(sysUserService.create(request));
    }

    /**
     * 修改用户。
     */
    @PutMapping("/{id}")
    @RequirePermission("system:user:update")
    @OperLog("修改用户")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid SysUserUpdateRequest request) {
        sysUserService.update(id, request);
        return Result.success();
    }

    /**
     * 删除用户。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("system:user:delete")
    @OperLog("删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.remove(id);
        return Result.success();
    }

    /**
     * 给用户分配角色。
     */
    @PutMapping("/{id}/roles")
    @RequirePermission("system:user:assign-roles")
    @OperLog("分配用户角色")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody @Valid AssignRolesRequest request) {
        sysUserService.assignRoles(id, request.getRoleIds());
        return Result.success();
    }

    /**
     * 重置用户密码。
     */
    @PutMapping("/{id}/password")
    @RequirePermission("system:user:reset-password")
    @OperLog("重置用户密码")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody @Valid ResetPasswordRequest request) {
        sysUserService.resetPassword(id, request.getNewPassword());
        return Result.success();
    }
}
