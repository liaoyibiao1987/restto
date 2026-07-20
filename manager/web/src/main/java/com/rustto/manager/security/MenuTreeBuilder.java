package com.rustto.manager.security;

import com.rustto.manager.dto.MenuTreeNode;
import com.rustto.manager.entity.SysMenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单扁平列表 → 树结构的纯函数工具（便于单测）。
 *
 * <p>规则：按 {@code parentId} 分组，自顶向下（parentId=0 为根）递归挂载子节点；
 * 同层按 {@code sort} 升序、再按 {@code id} 升序；孤儿节点（parent 不存在）按顶层处理。
 */
public final class MenuTreeBuilder {

    private MenuTreeBuilder() {
    }

    /**
     * 构建菜单树。
     *
     * @param menus 扁平菜单列表
     * @return 顶层节点列表（含子树）
     */
    public static List<MenuTreeNode> build(List<SysMenu> menus) {
        List<MenuTreeNode> empty = new ArrayList<>();
        if (menus == null || menus.isEmpty()) {
            return empty;
        }
        // 每个菜单只转换一次，后续根与子查询共用同一实例
        List<MenuTreeNode> nodes = new ArrayList<>();
        Set<Long> existIds = new HashSet<>();
        for (SysMenu m : menus) {
            nodes.add(toNode(m));
            if (m.getId() != null) {
                existIds.add(m.getId());
            }
        }
        Map<Long, List<MenuTreeNode>> byParent = new LinkedHashMap<>();
        for (MenuTreeNode n : nodes) {
            byParent.computeIfAbsent(parentOf(n), k -> new ArrayList<>()).add(n);
        }
        List<MenuTreeNode> roots = new ArrayList<>();
        for (MenuTreeNode n : nodes) {
            long pid = parentOf(n);
            if (pid == 0L || !existIds.contains(pid)) {
                roots.add(n);
            }
        }
        List<MenuTreeNode> sortedRoots = sortBy(roots);
        for (MenuTreeNode root : sortedRoots) {
            fillChildren(root, byParent);
        }
        return sortedRoots;
    }

    private static void fillChildren(MenuTreeNode parent, Map<Long, List<MenuTreeNode>> byParent) {
        List<MenuTreeNode> children = byParent.get(parent.getId());
        if (children == null || children.isEmpty()) {
            parent.setChildren(new ArrayList<>());
            return;
        }
        List<MenuTreeNode> sorted = sortBy(children);
        parent.setChildren(sorted);
        for (MenuTreeNode child : sorted) {
            fillChildren(child, byParent);
        }
    }

    private static List<MenuTreeNode> sortBy(List<MenuTreeNode> nodes) {
        List<MenuTreeNode> copy = new ArrayList<>(nodes);
        copy.sort(Comparator
                .comparing((MenuTreeNode n) -> n.getSort() == null ? 0 : n.getSort())
                .thenComparing(n -> n.getId() == null ? Long.MAX_VALUE : n.getId()));
        return copy;
    }

    private static long parentOf(MenuTreeNode n) {
        return n.getParentId() == null ? 0L : n.getParentId();
    }

    private static MenuTreeNode toNode(SysMenu m) {
        MenuTreeNode node = new MenuTreeNode();
        node.setId(m.getId());
        node.setParentId(m.getParentId());
        node.setMenuName(m.getMenuName());
        node.setMenuType(m.getMenuType());
        node.setPerms(m.getPerms());
        node.setPath(m.getPath());
        node.setComponent(m.getComponent());
        node.setIcon(m.getIcon());
        node.setSort(m.getSort());
        node.setVisible(m.getVisible());
        node.setStatus(m.getStatus());
        return node;
    }
}
