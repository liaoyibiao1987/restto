package com.restto.manager.dto.system.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 重置用户密码请求。
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
