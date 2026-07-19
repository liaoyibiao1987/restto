package com.rustto.manager.service;

import com.rustto.manager.dto.LoginRequest;
import com.rustto.manager.dto.LoginResponse;

/**
 * 鉴权服务。
 */
public interface AuthService {

    /**
     * 登录校验并签发 Token。
     *
     * @param request 登录请求
     * @return 登录响应（含 Token）
     */
    LoginResponse login(LoginRequest request);
}
