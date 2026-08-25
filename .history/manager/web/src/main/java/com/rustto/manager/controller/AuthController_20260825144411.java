package com.restto.manager.controller;

import com.restto.manager.common.Result;
import com.restto.manager.dto.LoginRequest;
import com.restto.manager.dto.LoginResponse;
import com.restto.manager.dto.MenuTreeNode;
import com.restto.manager.dto.UserInfoResponse;
import com.restto.manager.security.UserContext;
import com.restto.manager.service.AuthService;
import com.restto.manager.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 鉴权接口：登录、当前用户信息、当前用户菜单树。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final SysMenuService sysMenuService;

    /**
     * 登录。
     *
     * @param request 登录请求
     * @return 登录响应（含 Token、角色、权限）
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 当前登录用户信息（角色 + 权限码）。
     *
     * @return 用户信息
     */
    @GetMapping("/info")
    public Result<UserInfoResponse> info() {
        UserContext.CurrentUser user = UserContext.get();
        return Result.success(authService.loadUserInfo(user.userId));
    }

    /**
     * 当前登录用户可见的菜单树（用于动态渲染侧边栏）。
     *
     * @return 菜单树
     */
    @GetMapping("/menus")
    public Result<List<MenuTreeNode>> menus() {
        UserContext.CurrentUser user = UserContext.get();
        return Result.success(sysMenuService.treeByUserId(user.userId));
    }
}
