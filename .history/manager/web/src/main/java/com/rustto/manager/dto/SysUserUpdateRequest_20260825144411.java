package com.restto.manager.dto;

import lombok.Data;

/**
 * 修改用户请求（不含用户名与密码）。
 */
@Data
public class SysUserUpdateRequest {

    private String nickname;

    private String email;

    /** 0 停用 / 1 启用。 */
    private Integer status;
}
