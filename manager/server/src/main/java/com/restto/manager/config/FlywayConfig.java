package com.restto.manager.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 迁移策略配置。
 *
 * <p><b>背景：</b>连接「已有数据」的数据库时启动失败，日志表现为
 * {@code FlywayValidateException: Migration checksum mismatch for migration version 1/2/3}。
 * 原因是迁移脚本在代码结构调整（commit ee62039）中内容发生过变更，老库
 * {@code flyway_schema_history} 里记录的是旧脚本的 checksum，与当前 classpath
 * 脚本对不上，Flyway 校验拒绝启动。
 *
 * <p><b>方案：</b>采用 repair-and-retry 策略——首次 {@code migrate()} 失败时自动执行
 * {@code repair()} 对齐 checksum / 清理失败记录，再重试一次。安全性前提：本项目所有
 * 迁移脚本均为幂等脚本（{@code CREATE TABLE IF NOT EXISTS} + {@code INSERT IGNORE}），
 * 重放无副作用；修复后 pending 的迁移会正常补跑。
 *
 * <p>空库、checksum 一致等正常路径行为与默认策略完全一致（单次 migrate）；
 * 重试仍失败则异常继续上抛，让启动失败暴露真实问题。
 */
@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return new RepairAndRetryStrategy();
    }

    /**
     * 失败自愈的迁移策略：migrate 失败 → repair → 重试 migrate。
     */
    @Slf4j
    static class RepairAndRetryStrategy implements FlywayMigrationStrategy {

        @Override
        public void migrate(Flyway flyway) {
            try {
                flyway.migrate();
            } catch (FlywayException first) {
                log.warn("Flyway migrate 首次失败（多为老库 checksum 不一致），执行 repair 后重试：{}",
                        first.getMessage());
                flyway.repair();
                try {
                    flyway.migrate();
                } catch (FlywayException second) {
                    // 串联两次异常，保留完整因果链便于排查
                    second.addSuppressed(first);
                    throw second;
                }
                log.info("Flyway repair 后重试 migrate 成功");
            }
        }
    }
}
