package com.rustto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-菜单关联（复合主键）。service 层一律使用 {@code QueryWrapper}。
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    @TableId(type = IdType.INPUT)
    private Long roleId;

    private Long menuId;
}
