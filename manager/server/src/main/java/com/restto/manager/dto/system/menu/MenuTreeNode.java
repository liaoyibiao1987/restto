package com.restto.manager.dto.system.menu;

import lombok.Data;

import java.util.List;

/**
 * 菜单树节点（扁平 SysMenu 字段 + 子节点）。
 */
@Data
public class MenuTreeNode {

    private Long id;

    private Long parentId;

    private String menuName;

    private Integer menuType;

    private String perms;

    private String path;

    private String component;

    private String icon;

    private Integer sort;

    private Integer visible;

    private Integer status;

    private List<MenuTreeNode> children;
}
