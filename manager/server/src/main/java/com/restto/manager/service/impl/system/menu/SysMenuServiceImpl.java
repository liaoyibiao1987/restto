package com.restto.manager.service.impl.system.menu;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.restto.manager.common.BusinessException;
import com.restto.manager.common.ResultCode;
import com.restto.manager.dto.system.menu.MenuTreeNode;
import com.restto.manager.dto.system.menu.SysMenuRequest;
import com.restto.manager.entity.system.menu.SysMenu;
import com.restto.manager.mapper.SysMenuMapper;
import com.restto.manager.security.MenuTreeBuilder;
import com.restto.manager.service.system.menu.SysMenuService;
import com.restto.manager.service.system.role.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单服务实现：全量树 / 用户可见树 + CRUD（删除级联子菜单）。
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final SysRoleService sysRoleService;

    @Override
    public List<MenuTreeNode> tree() {
        List<SysMenu> menus = list(new QueryWrapper<SysMenu>().orderByAsc("sort", "id"));
        return MenuTreeBuilder.build(menus);
    }

    @Override
    public List<MenuTreeNode> treeByUserId(Long userId) {
        List<SysMenu> menus;
        if (sysRoleService.isAdmin(userId)) {
            // 含 visible=0 的隐藏路由（如工作流设计器）：前端用来注册路由，侧边栏按 visible 自行隐藏
            menus = list(new QueryWrapper<SysMenu>()
                    .eq("status", 1).orderByAsc("sort", "id"));
        } else {
            menus = baseMapper.selectMenusByUserId(userId);
        }
        return MenuTreeBuilder.build(menus);
    }

    @Override
    public SysMenu create(SysMenuRequest request) {
        SysMenu menu = new SysMenu();
        applyRequest(menu, request);
        save(menu);
        return menu;
    }

    @Override
    public void update(Long id, SysMenuRequest request) {
        SysMenu menu = mustGet(id);
        applyRequest(menu, request);
        updateById(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        mustGet(id);
        // 递归收集子菜单一并删除，避免遗留孤儿
        List<Long> toDelete = new ArrayList<>();
        collectDescendants(id, toDelete);
        if (!toDelete.isEmpty()) {
            removeByIds(toDelete);
        }
    }

    /** 收集自身 + 所有子孙 id。 */
    private void collectDescendants(Long rootId, List<Long> acc) {
        acc.add(rootId);
        List<SysMenu> children = list(new QueryWrapper<SysMenu>().eq("parent_id", rootId));
        for (SysMenu child : children) {
            collectDescendants(child.getId(), acc);
        }
    }

    private void applyRequest(SysMenu menu, SysMenuRequest request) {
        menu.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        menu.setMenuName(request.getMenuName());
        menu.setMenuType(request.getMenuType());
        menu.setPerms(request.getPerms());
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setIcon(request.getIcon());
        menu.setSort(request.getSort() == null ? 0 : request.getSort());
        menu.setVisible(request.getVisible() == null ? 1 : request.getVisible());
        menu.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private SysMenu mustGet(Long id) {
        SysMenu menu = getById(id);
        if (menu == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "菜单不存在");
        }
        return menu;
    }
}
