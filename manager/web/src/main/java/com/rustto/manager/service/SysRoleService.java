package com.rustto.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rustto.manager.common.PageResult;
import com.rustto.manager.dto.SysRoleCreateRequest;
import com.rustto.manager.dto.SysRoleUpdateRequest;
import com.rustto.manager.entity.SysRole;

import java.util.List;

/**
 * 角色服务。
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 分页查询角色。
     *
     * @param roleName 角色名片段（可空）
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    PageResult<SysRole> page(String roleName, long page, long size);

    /**
     * 新建角色。
     *
     * @param request 新建请求
     * @return 角色
     */
    SysRole create(SysRoleCreateRequest request);

    /**
     * 修改角色（role_code 不可改）。
     *
     * @param id      角色 ID
     * @param request 修改请求
     */
    void update(Long id, SysRoleUpdateRequest request);

    /**
     * 删除角色（级联清理 sys_role_menu / sys_role_permission / sys_user_role）。
     *
     * @param id 角色 ID
     */
    void remove(Long id);

    /**
     * 给角色分配菜单（事务内全量替换 sys_role_menu）。
     *
     * @param id      角色 ID
     * @param menuIds 菜单 ID 列表
     */
    void assignMenus(Long id, List<Long> menuIds);

    /**
     * 给角色分配权限（事务内全量替换 sys_role_permission）。
     *
     * @param id            角色 ID
     * @param permissionIds 权限 ID 列表
     */
    void assignPermissions(Long id, List<Long> permissionIds);

    /**
     * 取角色已分配的菜单 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    List<Long> getMenuIds(Long roleId);

    /**
     * 取角色已分配的权限 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    List<Long> getPermissionIds(Long roleId);

    /**
     * 判断用户是否为超管（绑定 admin 角色）。
     *
     * @param userId 用户 ID
     * @return 是否超管
     */
    boolean isAdmin(Long userId);

    /**
     * 取用户的角色编码列表。
     *
     * @param userId 用户 ID
     * @return 角色编码列表
     */
    List<String> getRoleCodes(Long userId);
}
