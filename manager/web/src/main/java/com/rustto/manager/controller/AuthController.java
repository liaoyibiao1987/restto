package com.rustto.manager.controller;

import com.rustto.manager.common.Result;
import com.rustto.manager.dto.LoginRequest;
import com.rustto.manager.dto.LoginResponse;
import com.rustto.manager.security.UserContext;
import com.rustto.manager.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 鉴权接口：登录、当前用户。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录。
     *
     * @param request 登录请求
     * @return 登录响应
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 当前登录用户信息。
     *
     * @return 用户信息
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        UserContext.CurrentUser user = UserContext.get();
        Map<String, Object> map = new HashMap<>();
        map.put("userId", user.userId);
        map.put("username", user.username);
        return Result.success(map);
    }
}
