package com.rustto.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rustto.manager.common.BusinessException;
import com.rustto.manager.common.PageResult;
import com.rustto.manager.common.ResultCode;
import com.rustto.manager.dto.SysUserCreateRequest;
import com.rustto.manager.dto.SysUserUpdateRequest;
import com.rustto.manager.dto.SysUserView;
import com.rustto.manager.entity.SysUser;
import com.rustto.manager.entity.SysUserRole;
import com.rustto.manager.mapper.SysRoleMapper;
import com.rustto.manager.mapper.SysUserMapper;
import com.rustto.manager.mapper.SysUserRoleMapper;
import com.rustto.manager.security.PermissionCache;
import com.rustto.manager.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 后台用户服务实现。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PermissionCache permissionCache;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public PageResult<SysUserView> page(String username, Integer status, long page, long size) {
        QueryWrapper<SysUser> qw = new QueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            qw.like("username", username);
        }
        if (status != null) {
            qw.eq("status", status);
        }
        qw.orderByDesc("created_at");
        Page<SysUser> p = new Page<>(page, size);
        Page<SysUser> result = page(p, qw);
        List<SysUserView> views = result.getRecords().stream()
                .map(this::toView).collect(Collectors.toList());
        return PageResult.of(page, size, result.getTotal(), views);
    }

    @Override
    public SysUserView view(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toView(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserView create(SysUserCreateRequest request) {
        if (getOne(new QueryWrapper<SysUser>().eq("username", request.getUsername())) != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "用户名已存在: " + request.getUsername());
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        save(user);
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            replaceRoles(user.getId(), request.getRoleIds());
        }
        return toView(user);
    }

    @Override
    public void update(Long id, SysUserUpdateRequest request) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        updateById(user);
        permissionCache.invalidate(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            return;
        }
        sysUserRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", id));
        removeById(id);
        permissionCache.invalidate(id);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        replaceRoles(id, roleIds == null ? Collections.emptyList() : roleIds);
        permissionCache.invalidate(id);
    }

    @Override
    public List<Long> getRoleIds(Long userId) {
        return sysUserRoleMapper.selectList(new QueryWrapper<SysUserRole>().eq("user_id", userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    /** 全量替换用户角色（先删后插）。 */
    private void replaceRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(new QueryWrapper<SysUserRole>().eq("user_id", userId));
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
    }

    /** 实体 → 视图（不暴露 passwordHash）。 */
    private SysUserView toView(SysUser user) {
        SysUserView view = new SysUserView();
        view.setId(user.getId());
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());
        view.setEmail(user.getEmail());
        view.setStatus(user.getStatus());
        view.setRole(user.getRole());
        view.setCreatedAt(user.getCreatedAt());
        List<Long> roleIds = getRoleIds(user.getId());
        view.setRoleIds(roleIds);
        if (roleIds.isEmpty()) {
            view.setRoleCodes(new ArrayList<>());
        } else {
            view.setRoleCodes(sysRoleMapper.selectRoleCodesByUserId(user.getId()));
        }
        return view;
    }
}
