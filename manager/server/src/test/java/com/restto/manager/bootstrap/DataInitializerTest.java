package com.restto.manager.bootstrap;

import com.restto.manager.entity.SysRole;
import com.restto.manager.entity.SysUser;
import com.restto.manager.entity.SysUserRole;
import com.restto.manager.mapper.SysRoleMapper;
import com.restto.manager.mapper.SysUserMapper;
import com.restto.manager.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 启动期种子数据初始化测试（连接已有数据的库时不误伤存量数据）。
 */
class DataInitializerTest {

    /** 与 DataInitializer.LEGACY_BAD_HASH 保持一致（V1 种子脚本误用的错误哈希）。 */
    private static final String LEGACY_BAD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private SysUserMapper sysUserMapper;
    private SysRoleMapper sysRoleMapper;
    private SysUserRoleMapper sysUserRoleMapper;
    private DataInitializer initializer;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final DefaultApplicationArguments args = new DefaultApplicationArguments();

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        sysRoleMapper = mock(SysRoleMapper.class);
        sysUserRoleMapper = mock(SysUserRoleMapper.class);
        initializer = new DataInitializer(sysUserMapper, sysRoleMapper, sysUserRoleMapper);
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "admin123");
    }

    private SysUser existingAdmin(String hash) {
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash(hash);
        return admin;
    }

    private SysRole adminRole() {
        SysRole role = new SysRole();
        role.setId(2L);
        role.setRoleCode("admin");
        return role;
    }

    @Test
    void createsAdminWhenMissing() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);

        initializer.run(args);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        assertEquals("admin", captor.getValue().getUsername());
        assertTrue(encoder.matches("admin123", captor.getValue().getPasswordHash()),
                "种子密码须按配置明文 admin123 加密落库");
    }

    @Test
    void upgradesLegacyBadHashWithoutInsert() {
        when(sysUserMapper.selectOne(any())).thenReturn(existingAdmin(LEGACY_BAD_HASH));
        when(sysRoleMapper.selectOne(any())).thenReturn(adminRole());
        when(sysUserRoleMapper.selectOne(any())).thenReturn(null);

        initializer.run(args);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertTrue(encoder.matches("admin123", captor.getValue().getPasswordHash()),
                "旧错误哈希须被升级为 admin123 的新哈希");
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void skipsHealthyAdminAndExistingBinding() {
        when(sysUserMapper.selectOne(any())).thenReturn(existingAdmin(encoder.encode("admin123")));
        when(sysRoleMapper.selectOne(any())).thenReturn(adminRole());
        when(sysUserRoleMapper.selectOne(any())).thenReturn(new SysUserRole());

        initializer.run(args);

        verify(sysUserMapper, never()).insert(any(SysUser.class));
        verify(sysUserMapper, never()).updateById(any(SysUser.class));
        verify(sysUserRoleMapper, never()).insert(any(SysUserRole.class));
    }

    @Test
    void bindsAdminRoleWhenBindingMissing() {
        when(sysUserMapper.selectOne(any())).thenReturn(existingAdmin(encoder.encode("admin123")));
        when(sysRoleMapper.selectOne(any())).thenReturn(adminRole());
        when(sysUserRoleMapper.selectOne(any())).thenReturn(null);

        initializer.run(args);

        ArgumentCaptor<SysUserRole> captor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(sysUserRoleMapper).insert(captor.capture());
        assertEquals(Long.valueOf(1L), captor.getValue().getUserId());
        assertEquals(Long.valueOf(2L), captor.getValue().getRoleId());
    }

    @Test
    void seedFailureDoesNotBreakStartup() {
        // 种子异常只记日志，不阻断应用启动
        when(sysUserMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));

        initializer.run(args);

        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }
}
