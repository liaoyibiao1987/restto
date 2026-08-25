package com.restto.manager.controller;

import com.restto.manager.common.Result;
import com.restto.manager.dto.MenuTreeNode;
import com.restto.manager.dto.SysMenuRequest;
import com.restto.manager.entity.SysMenu;
import com.restto.manager.security.OperLog;
import com.restto.manager.security.RequirePermission;
import com.restto.manager.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 菜单接口。
 */
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
@RequirePermission("system:menu:list")
public class SysMenuController {

    private final SysMenuService sysMenuService;

    /**
     * 全量菜单树。
     */
    @GetMapping
    public Result<List<MenuTreeNode>> tree() {
        return Result.success(sysMenuService.tree());
    }

    /**
     * 菜单详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("system:menu:query")
    public Result<SysMenu> get(@PathVariable Long id) {
        return Result.success(sysMenuService.getById(id));
    }

    /**
     * 新建菜单。
     */
    @PostMapping
    @RequirePermission("system:menu:create")
    @OperLog("新增菜单")
    public Result<SysMenu> create(@RequestBody @Valid SysMenuRequest request) {
        return Result.success(sysMenuService.create(request));
    }

    /**
     * 修改菜单。
     */
    @PutMapping("/{id}")
    @RequirePermission("system:menu:update")
    @OperLog("修改菜单")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid SysMenuRequest request) {
        sysMenuService.update(id, request);
        return Result.success();
    }

    /**
     * 删除菜单（含子菜单）。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("system:menu:delete")
    @OperLog("删除菜单")
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.remove(id);
        return Result.success();
    }
}
