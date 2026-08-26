package com.restto.manager.service.system.auth;

import com.restto.manager.dto.system.auth.LoginRequest;
import com.restto.manager.dto.system.auth.LoginResponse;
import com.restto.manager.dto.system.auth.UserInfoResponse;

/**
 * 鉴权服务。
 */
public interface AuthService {

    /**
     * 登录校验并签发 Token。
     *
     * @param request 登录请求
     * @return 登录响应（含 Token、角色、权限）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 加载当前登录用户信息（角色 + 权限码）。
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    UserInfoResponse loadUserInfo(Long userId);
}

