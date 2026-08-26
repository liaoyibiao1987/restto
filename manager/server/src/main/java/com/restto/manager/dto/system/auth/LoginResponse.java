package com.restto.manager.dto.system.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

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

    /**
     * 单角色字符串（向后兼容）。
     *
     * @deprecated RBAC 改造后改用 {@link #roles}，本字段仅保留一个版本做兼容，不再作为鉴权依据。
     */
    @Deprecated
    private String role;

    /** 角色编码集合。 */
    private List<String> roles;

    /** 权限码集合。 */
    private List<String> permissions;
}
