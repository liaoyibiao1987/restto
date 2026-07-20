package com.rustto.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rustto.manager.common.BusinessException;
import com.rustto.manager.common.PageResult;
import com.rustto.manager.common.ResultCode;
import com.rustto.manager.dto.SysRoleCreateRequest;
import com.rustto.manager.dto.SysRoleUpdateRequest;
import com.rustto.manager.entity.SysRole;
import com.rustto.manager.entity.SysRoleMenu;
import com.rustto.manager.entity.SysRolePermission;
import com.rustto.manager.entity.SysUserRole;
import com.rustto.manager.mapper.SysRoleMapper;
import com.rustto.manager.mapper.SysRoleMenuMapper;
import com.rustto.manager.mapper.SysRolePermissionMapper;
import com.rustto.manager.mapper.SysUserRoleMapper;
import com.rustto.manager.security.PermissionCache;
import com.rustto.manager.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现：CRUD + 菜单/权限分配 + 超管判断。
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PermissionCache permissionCache;

    @Override
    public PageResult<SysRole> page(String roleName, long page, long size) {
        QueryWrapper<SysRole> qw = new QueryWrapper<>();
        if (roleName != null && !roleName.isEmpty()) {
            qw.like("role_name", roleName);
        }
        qw.orderByAsc("id");
        Page<SysRole> p = new Page<>(page, size);
        Page<SysRole> result = page(p, qw);
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }

    @Override
    public SysRole create(SysRoleCreateRequest request) {
        if (getOne(new QueryWrapper<SysRole>().eq("role_code", request.getRoleCode())) != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "角色编码已存在: " + request.getRoleCode());
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setRemark(request.getRemark());
        role.setStatus(1);
        save(role);
        return role;
    }

    @Override
    public void update(Long id, SysRoleUpdateRequest request) {
        SysRole role = mustGet(id);
        if (request.getRoleName() != null) {
            role.setRoleName(request.getRoleName());
        }
        if (request.getStatus() != null) {
            role.setStatus(request.getStatus());
        }
        if (request.getRemark() != null) {
            role.setRemark(request.getRemark());
        }
        updateById(role);
        // 状态变更可能影响权限可见性，统一失效
        permissionCache.invalidateAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SysRole role = mustGet(id);
        if ("admin".equals(role.getRoleCode())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "内置超管角色不可删除");
        }
        sysRoleMenuMapper.delete(new QueryWrapper<SysRoleMenu>().eq("role_id", id));
        sysRolePermissionMapper.delete(new QueryWrapper<SysRolePermission>().eq("role_id", id));
        sysUserRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("role_id", id));
        removeById(id);
        permissionCache.invalidateAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long id, List<Long> menuIds) {
        mustGet(id);
        sysRoleMenuMapper.delete(new QueryWrapper<SysRoleMenu>().eq("role_id", id));
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(id);
                rm.setMenuId(menuId);
                sysRoleMenuMapper.insert(rm);
            }
        }
        // 菜单变更不改变权限码，但保守失效一次以刷新侧边栏语义一致性
        permissionCache.invalidateAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long id, List<Long> permissionIds) {
        mustGet(id);
        sysRolePermissionMapper.delete(new QueryWrapper<SysRolePermission>().eq("role_id", id));
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(id);
                rp.setPermissionId(pid);
                sysRolePermissionMapper.insert(rp);
            }
        }
        permissionCache.invalidateAll();
    }

    @Override
    public List<Long> getMenuIds(Long roleId) {
        return sysRoleMenuMapper.selectList(new QueryWrapper<SysRoleMenu>().eq("role_id", roleId))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Override
    public List<Long> getPermissionIds(Long roleId) {
        return sysRolePermissionMapper.selectList(new QueryWrapper<SysRolePermission>().eq("role_id", roleId))
                .stream().map(SysRolePermission::getPermissionId).collect(Collectors.toList());
    }

    @Override
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return baseMapper.existsAdminForUser(userId) > 0;
    }

    @Override
    public List<String> getRoleCodes(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        return baseMapper.selectRoleCodesByUserId(userId);
    }

    private SysRole mustGet(Long id) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }
}
