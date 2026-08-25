package com.restto.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restto.manager.common.PageResult;
import com.restto.manager.dto.SysUserCreateRequest;
import com.restto.manager.dto.SysUserUpdateRequest;
import com.restto.manager.dto.SysUserView;
import com.restto.manager.entity.SysUser;

import java.util.List;

/**
 * 后台用户服务。
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 分页查询用户（可按用户名模糊、状态过滤）。
     *
     * @param username 用户名片段（可空）
     * @param status   状态（可空）
     * @param page     页码
     * @param size     每页大小
     * @return 用户视图分页
     */
    PageResult<SysUserView> page(String username, Integer status, long page, long size);

    /**
     * 查看用户详情（含角色）。
     *
     * @param id 用户 ID
     * @return 用户视图
     */
    SysUserView view(Long id);

    /**
     * 新建用户（BCrypt 加密密码，可选分配角色）。
     *
     * @param request 新建请求
     * @return 用户视图
     */
    SysUserView create(SysUserCreateRequest request);

    /**
     * 修改用户（昵称/邮箱/状态）。
     *
     * @param id      用户 ID
     * @param request 修改请求
     */
    void update(Long id, SysUserUpdateRequest request);

    /**
     * 删除用户（级联清理 sys_user_role，失效其权限缓存）。
     *
     * @param id 用户 ID
     */
    void remove(Long id);

    /**
     * 重置用户密码（BCrypt）。
     *
     * @param id          用户 ID
     * @param newPassword 新密码明文
     */
    void resetPassword(Long id, String newPassword);

    /**
     * 给用户分配角色（事务内全量替换 sys_user_role）。
     *
     * @param id       用户 ID
     * @param roleIds  角色 ID 列表
     */
    void assignRoles(Long id, List<Long> roleIds);

    /**
     * 取用户的角色 ID 列表。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    List<Long> getRoleIds(Long userId);
}
