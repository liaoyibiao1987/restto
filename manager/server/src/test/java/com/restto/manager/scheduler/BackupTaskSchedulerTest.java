package com.restto.manager.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务调度器 cron 匹配测试。
 */
class BackupTaskSchedulerTest {

    @Test
    void emptyCronNeverDue() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 9, 30);
        assertFalse(BackupTaskScheduler.isDue(null, now));
        assertFalse(BackupTaskScheduler.isDue("", now));
        assertFalse(BackupTaskScheduler.isDue("   ", now));
    }

    @Test
    void invalidCronNeverDue() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 9, 30);
        assertFalse(BackupTaskScheduler.isDue("not a cron", now));
    }

    @Test
    void matchesAtSpecificMinute() {
        // 每天 09:30 命中
        LocalDateTime hit = LocalDateTime.of(2026, 7, 18, 9, 30);
        LocalDateTime miss = LocalDateTime.of(2026, 7, 18, 9, 31);
        assertTrue(BackupTaskScheduler.isDue("0 30 9 * * *", hit));
        assertFalse(BackupTaskScheduler.isDue("0 30 9 * * *", miss));
    }

    @Test
    void everyMinuteMatchesAtMinuteBoundary() {
        // 每分钟的第 0 秒触发：对齐到分钟的 now 必然命中
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 9, 30);
        assertTrue(BackupTaskScheduler.isDue("0 * * * * *", now));
    }
}
