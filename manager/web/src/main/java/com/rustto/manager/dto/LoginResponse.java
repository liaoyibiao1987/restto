package com.rustto.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录响应。
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    /** JWT Token。 */
    private String token;

    /** 用户名。 */
    private String username;

    /** 角色。 */
    private String role;
}
