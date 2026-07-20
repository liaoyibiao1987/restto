package com.rustto.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rustto.manager.common.BusinessException;
import com.rustto.manager.common.ResultCode;
import com.rustto.manager.dto.LoginRequest;
import com.rustto.manager.dto.LoginResponse;
import com.rustto.manager.dto.UserInfoResponse;
import com.rustto.manager.entity.SysUser;
import com.rustto.manager.mapper.SysUserMapper;
import com.rustto.manager.security.JwtUtil;
import com.rustto.manager.service.AuthService;
import com.rustto.manager.service.SysPermissionService;
import com.rustto.manager.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 鉴权服务实现：BCrypt 校验密码 + JWT 签发；用户信息/角色/权限以 DB 为准（缓存）。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;

    private final JwtUtil jwtUtil;

    private final SysRoleService sysRoleService;

    private final SysPermissionService sysPermissionService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已禁用");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        List<String> roles = sysRoleService.getRoleCodes(user.getId());
        List<String> perms = new ArrayList<>(sysPermissionService.getPermissionCodes(user.getId()));
        // role 字段保留做向后兼容；鉴权一律以 sys_user_role 为准。
        @SuppressWarnings("deprecation")
        String legacyRole = user.getRole();
        return new LoginResponse(token, user.getUsername(), legacyRole, roles, perms);
    }

    @Override
    public UserInfoResponse loadUserInfo(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        UserInfoResponse info = new UserInfoResponse();
        info.setUserId(user.getId());
        info.setUsername(user.getUsername());
        info.setNickname(user.getNickname());
        info.setRoles(sysRoleService.getRoleCodes(userId));
        info.setPermissionCodes(new ArrayList<>(sysPermissionService.getPermissionCodes(userId)));
        return info;
    }
}
