package com.restto.manager.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.restto.manager.entity.system.role.SysRole;
import com.restto.manager.entity.system.user.SysUser;
import com.restto.manager.entity.system.user.SysUserRole;
import com.restto.manager.mapper.SysRoleMapper;
import com.restto.manager.mapper.SysUserMapper;
import com.restto.manager.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动期数据初始化与升级。
 *
 * <p>独立于 Flyway 迁移脚本：表结构/历史迁移仍由 migrations 负责（只增不改不删），
 * 这里负责在上下文就绪后对「种子数据」做幂等的初始化或自愈升级，便于后续扩展
 * （如默认节点、版本记录等均可在此追加 {@code ensureXxx()} 方法）。
 *
 * <p>当前职责：
 * <ul>
 *   <li>{@link #ensureAdminUser()} 确保内置管理员账号可用（不存在则创建、旧错误哈希则升级）；</li>
 *   <li>{@link #ensureAdminUserRole()} 把内置管理员绑定到 {@code admin} 角色（RBAC 改造后鉴权依据）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    /** V1 种子脚本中误用的错误哈希（对应明文 {@code password}，非 {@code admin123}）。 */
    private static final String LEGACY_BAD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final SysUserMapper sysUserMapper;

    private final SysRoleMapper sysRoleMapper;

    private final SysUserRoleMapper sysUserRoleMapper;

    /** 与 {@link com.restto.manager.service.impl.system.auth.AuthServiceImpl} 保持一致的编码器惯例。 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${restto.init.admin-username:admin}")
    private String adminUsername;

    @Value("${restto.init.admin-password:admin123}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureAdminUser();
            ensureAdminUserRole();
        } catch (Exception e) {
            // 种子失败不应阻断应用启动，仅记录错误。
            log.error("启动期数据初始化失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 确保内置管理员账号存在且密码正确（幂等）。
     */
    private void ensureAdminUser() {
        SysUser admin = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", adminUsername));

        if (admin == null) {
            SysUser user = new SysUser();
            user.setUsername(adminUsername);
            user.setPasswordHash(passwordEncoder.encode(adminPassword));
            user.setStatus(1);
            sysUserMapper.insert(user);
            log.info("初始化管理员账号：{}", adminUsername);
            return;
        }

        if (LEGACY_BAD_HASH.equals(admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            sysUserMapper.updateById(admin);
            log.info("升级管理员账号 {} 的旧哈希为正确密码", adminUsername);
            return;
        }

        log.debug("管理员账号 {} 已存在且密码非旧值，跳过", adminUsername);
    }

    /**
     * 确保内置管理员绑定到 {@code admin} 角色（幂等、自愈）。
     *
     * <p>RBAC 改造后，登录返回的角色/权限与超管 bypass 均依赖该绑定；
     * V2 迁移已写入，此处兜底自愈（如 DB 被手动清理后重启）。
     */
    private void ensureAdminUserRole() {
        SysUser admin = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", adminUsername));
        if (admin == null) {
            log.warn("管理员账号 {} 不存在，跳过角色绑定", adminUsername);
            return;
        }
        SysRole adminRole = sysRoleMapper.selectOne(
                new QueryWrapper<SysRole>().eq("role_code", "admin"));
        if (adminRole == null) {
            log.warn("admin 角色不存在，跳过角色绑定（请检查 V2 迁移是否成功执行）");
            return;
        }
        SysUserRole exist = sysUserRoleMapper.selectOne(new QueryWrapper<SysUserRole>()
                .eq("user_id", admin.getId()).eq("role_id", adminRole.getId()));
        if (exist == null) {
            SysUserRole rel = new SysUserRole();
            rel.setUserId(admin.getId());
            rel.setRoleId(adminRole.getId());
            sysUserRoleMapper.insert(rel);
            log.info("绑定管理员账号 {} → admin 角色", adminUsername);
        }
    }
}

