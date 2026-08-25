package com.restto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台用户实体（对应 sys_user 表）。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    /**
     * @deprecated 自 RBAC 改造起不再作为鉴权依据，改由 sys_user_role 决定；仅保留做向后兼容展示。
     */
    @Deprecated
    private String role;

    /** 0 停用 / 1 启用。 */
    private Integer status;

    private String nickname;

    private String email;

    private LocalDateTime createdAt;
}
