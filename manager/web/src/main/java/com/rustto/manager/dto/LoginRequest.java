package com.rustto.manager.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 登录请求。
 */
@Data
public class LoginRequest {

    /** 用户名。 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码。 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
