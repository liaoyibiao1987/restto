package com.restto.manager.entity.system.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限关联（复合主键）。service 层一律使用 {@code QueryWrapper}。
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.INPUT)
    private Long roleId;

    private Long permissionId;
}
