package com.restto.manager.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图（列表/详情响应投影，绝不回传 passwordHash）。
 */
@Data
public class SysUserView {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    /** 0 停用 / 1 启用。 */
    private Integer status;

    private String role;

    private LocalDateTime createdAt;

    /** 已分配角色 ID。 */
    private List<Long> roleIds;

    /** 已分配角色编码（便于前端展示）。 */
    private List<String> roleCodes;
}
