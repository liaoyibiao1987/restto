package com.restto.manager.security;

import com.restto.manager.dto.system.menu.MenuTreeNode;
import com.restto.manager.entity.system.menu.SysMenu;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MenuTreeBuilder} 单测：扁平→树、孤儿、排序、空。
 */
class MenuTreeBuilderTest {

    private SysMenu menu(long id, long parent, String name, int sort) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setParentId(parent);
        m.setMenuName(name);
        m.setSort(sort);
        m.setMenuType(2);
        return m;
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertTrue(MenuTreeBuilder.build(null).isEmpty());
        assertTrue(MenuTreeBuilder.build(Collections.emptyList()).isEmpty());
    }

    @Test
    void buildsNestedTreeByParentId() {
        List<SysMenu> flat = Arrays.asList(
                menu(1, 0, "root", 0),
                menu(2, 1, "child", 0),
                menu(3, 2, "grandchild", 0));
        List<MenuTreeNode> tree = MenuTreeBuilder.build(flat);
        assertEquals(1, tree.size());
        assertEquals("root", tree.get(0).getMenuName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("grandchild", tree.get(0).getChildren().get(0).getChildren().get(0).getMenuName());
    }

    @Test
    void sortsBySortThenId() {
        List<SysMenu> flat = new ArrayList<>(Arrays.asList(
                menu(3, 0, "c", 2),
                menu(1, 0, "a", 1),
                menu(2, 0, "b", 1)));
        List<MenuTreeNode> tree = MenuTreeBuilder.build(flat);
        assertEquals("a", tree.get(0).getMenuName());
        assertEquals("b", tree.get(1).getMenuName());
        assertEquals("c", tree.get(2).getMenuName());
    }

    @Test
    void orphanBranchTreatedAsRoot() {
        // parent 指向不存在的 id → 视作顶层
        List<SysMenu> flat = Collections.singletonList(menu(9, 999, "orphan", 0));
        List<MenuTreeNode> tree = MenuTreeBuilder.build(flat);
        assertEquals(1, tree.size());
        assertEquals("orphan", tree.get(0).getMenuName());
        assertTrue(tree.get(0).getChildren().isEmpty());
    }
}
