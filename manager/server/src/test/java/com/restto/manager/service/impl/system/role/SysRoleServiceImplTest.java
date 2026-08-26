package com.restto.manager.service.impl.system.role;

import com.restto.manager.common.BusinessException;
import com.restto.manager.entity.system.role.SysRole;
import com.restto.manager.mapper.SysRoleMapper;
import com.restto.manager.mapper.SysRoleMenuMapper;
import com.restto.manager.mapper.SysRolePermissionMapper;
import com.restto.manager.mapper.SysUserRoleMapper;
import com.restto.manager.security.PermissionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysRoleServiceImpl} 单测：超管判断、权限分配、内置角色保护。
 */
class SysRoleServiceImplTest {

    private SysRoleMapper roleMapper;

    private SysRolePermissionMapper rolePermMapper;

    private SysRoleMenuMapper roleMenuMapper;

    private SysUserRoleMapper userRoleMapper;

    private PermissionCache cache;

    private SysRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        roleMapper = mock(SysRoleMapper.class);
        rolePermMapper = mock(SysRolePermissionMapper.class);
        roleMenuMapper = mock(SysRoleMenuMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        cache = mock(PermissionCache.class);
        service = new SysRoleServiceImpl(roleMenuMapper, rolePermMapper, userRoleMapper, cache);
        // ServiceImpl 的 baseMapper 由框架注入，单测中用反射塞入 mock
        ReflectionTestUtils.setField(service, "baseMapper", roleMapper);
    }

    @Test
    void isAdminTrueWhenAdminRoleBound() {
        when(roleMapper.existsAdminForUser(1L)).thenReturn(1);
        assertTrue(service.isAdmin(1L));
    }

    @Test
    void isAdminFalseOtherwise() {
        when(roleMapper.existsAdminForUser(2L)).thenReturn(0);
        assertFalse(service.isAdmin(2L));
    }

    @Test
    void isAdminFalseForNullUser() {
        assertFalse(service.isAdmin(null));
    }

    @Test
    void assignPermissionsReplacesAndInvalidatesCache() {
        SysRole role = new SysRole();
        role.setId(5L);
        when(roleMapper.selectById(5L)).thenReturn(role);
        when(rolePermMapper.delete(any())).thenReturn(1);
        when(rolePermMapper.insert(any())).thenReturn(1);

        service.assignPermissions(5L, Arrays.asList(10L, 20L));

        verify(rolePermMapper).delete(any());
        verify(rolePermMapper, times(2)).insert(any());
        verify(cache).invalidateAll();
    }

    @Test
    void removeBuiltinAdminRoleRejected() {
        SysRole admin = new SysRole();
        admin.setId(1L);
        admin.setRoleCode("admin");
        when(roleMapper.selectById(1L)).thenReturn(admin);
        assertThrows(BusinessException.class, () -> service.remove(1L));
    }
}
