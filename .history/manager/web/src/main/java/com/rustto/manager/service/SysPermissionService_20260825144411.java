package com.restto.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restto.manager.common.PageResult;
import com.restto.manager.entity.SysPermission;

import java.util.Set;

/**
 * 权限点服务。
 */
public interface SysPermissionService extends IService<SysPermission> {

    /**
     * 分页查询权限点（可按模块过滤）。
     *
     * @param module 模块（可空）
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    PageResult<SysPermission> page(String module, long page, long size);

    /**
     * 取用户全部权限码（缓存）。
     *
     * @param userId 用户 ID
     * @return 权限码集合
     */
    Set<String> getPermissionCodes(Long userId);
}
