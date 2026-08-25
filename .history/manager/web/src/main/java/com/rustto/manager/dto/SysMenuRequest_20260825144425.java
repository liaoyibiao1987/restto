package com.restto.manager.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 菜单新增/修改请求。
 */
@Data
public class SysMenuRequest {

    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    private String menuName;

    /** 1 目录 2 菜单。 */
    @NotNull(message = "菜单类型不能为空")
    private Integer menuType;

    private String perms;

    private String path;

    private String component;

    private String icon;

    private Integer sort;

    private Integer visible;

    private Integer status;
}
