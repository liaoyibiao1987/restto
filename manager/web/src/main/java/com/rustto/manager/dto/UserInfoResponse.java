package com.rustto.manager.dto;

import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息（{@code /api/auth/info} 响应）。
 */
@Data
public class UserInfoResponse {

    private Long userId;

    private String username;

    private String nickname;

    private List<String> roles;

    private List<String> permissionCodes;
}
