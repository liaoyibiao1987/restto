package com.restto.manager.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Flyway repair-and-retry 迁移策略测试。
 *
 * <p>覆盖四个分支：正常路径不打扰、checksum 不一致自愈、重试仍失败上抛、
 * repair 自身失败上抛（不做无意义重试）。
 */
class FlywayConfigTest {

    private Flyway flyway;
    private FlywayConfig.RepairAndRetryStrategy strategy;

    @BeforeEach
    void setUp() {
        flyway = mock(Flyway.class);
        strategy = new FlywayConfig.RepairAndRetryStrategy();
    }

    @Test
    void happyPathMigratesOnceWithoutRepair() {
        when(flyway.migrate()).thenReturn(null);

        strategy.migrate(flyway);

        verify(flyway, times(1)).migrate();
        verify(flyway, never()).repair();
    }

    @Test
    void checksumMismatchRepairsThenRetries() {
        // 首次 migrate 抛 checksum 不一致（老库场景），repair 对齐后重试成功
        when(flyway.migrate())
                .thenThrow(new FlywayException("Migration checksum mismatch for migration version 1"))
                .thenReturn(null);

        strategy.migrate(flyway);

        verify(flyway).repair();
        verify(flyway, times(2)).migrate();
    }

    @Test
    void retryFailurePropagatesWithFirstFailureSuppressed() {
        FlywayException first = new FlywayException("checksum mismatch");
        FlywayException second = new FlywayException("still failing after repair");
        when(flyway.migrate()).thenThrow(first, second);

        FlywayException thrown = assertThrows(FlywayException.class, () -> strategy.migrate(flyway));

        assertSame(second, thrown);
        assertSame(first, thrown.getSuppressed()[0]);
        verify(flyway).repair();
        verify(flyway, times(2)).migrate();
    }

    @Test
    void repairFailurePropagatesWithoutRetry() {
        // 连接类错误（如 Access denied）repair 同样失败：直接上抛，不再盲目重试
        when(flyway.migrate()).thenThrow(new FlywayException("Unable to obtain connection from database"));
        when(flyway.repair()).thenThrow(new FlywayException("repair failed"));

        assertThrows(FlywayException.class, () -> strategy.migrate(flyway));

        verify(flyway, times(1)).migrate();
        verify(flyway).repair();
    }
}
