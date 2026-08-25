package com.restto.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.restto.manager.common.PageResult;
import com.restto.manager.entity.SysPermission;
import com.restto.manager.mapper.SysPermissionMapper;
import com.restto.manager.security.PermissionCache;
import com.restto.manager.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 权限点服务实现：分页查询 + 带缓存的用户权限码读取。
 */
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission>
        implements SysPermissionService {

    private final PermissionCache permissionCache;

    @Override
    public PageResult<SysPermission> page(String module, long page, long size) {
        QueryWrapper<SysPermission> qw = new QueryWrapper<>();
        if (module != null && !module.isEmpty()) {
            qw.eq("module", module);
        }
        qw.orderByAsc("module", "id");
        Page<SysPermission> p = new Page<>(page, size);
        Page<SysPermission> result = page(p, qw);
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }

    @Override
    public Set<String> getPermissionCodes(Long userId) {
        // 缓存未命中时执行 join 查询；权限变更由调用方主动失效缓存，兜底 TTL。
        return permissionCache.getOrLoad(userId,
                () -> new HashSet<>(baseMapper.selectPermissionCodesByUserId(userId)));
    }
}
