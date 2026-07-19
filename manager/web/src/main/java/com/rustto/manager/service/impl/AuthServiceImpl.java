package com.rustto.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rustto.manager.common.BusinessException;
import com.rustto.manager.common.ResultCode;
import com.rustto.manager.dto.LoginRequest;
import com.rustto.manager.dto.LoginResponse;
import com.rustto.manager.entity.SysUser;
import com.rustto.manager.mapper.SysUserMapper;
import com.rustto.manager.security.JwtUtil;
import com.rustto.manager.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 鉴权服务实现：BCrypt 校验密码 + JWT 签发。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;

    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getUsername(), user.getRole());
    }
}
