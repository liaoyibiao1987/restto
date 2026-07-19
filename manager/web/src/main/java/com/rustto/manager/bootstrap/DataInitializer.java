package com.rustto.manager.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rustto.manager.entity.SysUser;
import com.rustto.manager.mapper.SysUserMapper;
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
 * <p>当前职责：确保内置管理员账号可用。
 * <ul>
 *   <li>不存在 → 用 BCrypt 加密后的默认密码创建；</li>
 *   <li>存在但哈希为已知的错误旧值（V1 种子误用了 {@code password} 的范例哈希）→ 升级为正确哈希；</li>
 *   <li>其余情况（管理员已自行改密）→ 保持不动。</li>
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

    /** 与 {@link com.rustto.manager.service.impl.AuthServiceImpl} 保持一致的编码器惯例。 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${rustto.init.admin-username:admin}")
    private String adminUsername;

    @Value("${rustto.init.admin-password:admin123}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureAdminUser();
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
            user.setRole("admin");
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
}
