package com.rustto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单实体（对应 sys_menu 表，树形：parent_id=0 为顶层）。
 */
@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;

    private String menuName;

    /** 1 目录 2 菜单。 */
    private Integer menuType;

    /** 关联权限码（可空）。 */
    private String perms;

    private String path;

    private String component;

    private String icon;

    private Integer sort;

    /** 0 隐藏 / 1 显示。 */
    private Integer visible;

    /** 0 停用 / 1 启用。 */
    private Integer status;

    private LocalDateTime createdAt;
}
