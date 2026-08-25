package com.restto.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restto.manager.dto.MenuTreeNode;
import com.restto.manager.dto.SysMenuRequest;
import com.restto.manager.entity.SysMenu;

import java.util.List;

/**
 * 菜单服务。
 */
public interface SysMenuService extends IService<SysMenu> {

    /**
     * 全量菜单树（管理端，不过滤可见性）。
     *
     * @return 菜单树
     */
    List<MenuTreeNode> tree();

    /**
     * 当前用户可见的菜单树（用于侧边栏渲染）。
     *
     * @param userId 用户 ID
     * @return 菜单树
     */
    List<MenuTreeNode> treeByUserId(Long userId);

    /**
     * 新建菜单。
     *
     * @param request 菜单请求
     * @return 菜单
     */
    SysMenu create(SysMenuRequest request);

    /**
     * 修改菜单。
     *
     * @param id      菜单 ID
     * @param request 菜单请求
     */
    void update(Long id, SysMenuRequest request);

    /**
     * 删除菜单（含子菜单级联）。
     *
     * @param id 菜单 ID
     */
    void remove(Long id);
}
