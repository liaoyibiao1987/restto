package com.rustto.manager.service.impl;

import com.rustto.manager.entity.SysUser;
import com.rustto.manager.mapper.SysRoleMapper;
import com.rustto.manager.mapper.SysUserMapper;
import com.rustto.manager.mapper.SysUserRoleMapper;
import com.rustto.manager.security.PermissionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysUserServiceImpl} 单测：重置密码、分配角色、缓存失效。
 */
class SysUserServiceImplTest {

    private SysUserMapper userMapper;

    private SysUserRoleMapper userRoleMapper;

    private SysRoleMapper roleMapper;

    private PermissionCache cache;

    private SysUserServiceImpl service;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        cache = mock(PermissionCache.class);
        service = new SysUserServiceImpl(userRoleMapper, roleMapper, cache);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
    }

    @Test
    void resetPasswordBcryptHashesAndUpdates() {
        SysUser user = new SysUser();
        user.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);

        service.resetPassword(1L, "newpass");

        verify(userMapper).updateById(any(SysUser.class));
        assertTrue(encoder.matches("newpass", user.getPasswordHash()));
    }

    @Test
    void assignRolesReplacesAndInvalidatesCache() {
        SysUser user = new SysUser();
        user.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userRoleMapper.delete(any())).thenReturn(0);
        when(userRoleMapper.insert(any())).thenReturn(1);

        service.assignRoles(7L, Arrays.asList(2L, 3L));

        verify(userRoleMapper).delete(any());
        verify(userRoleMapper, times(2)).insert(any());
        verify(cache).invalidate(7L);
    }
}
