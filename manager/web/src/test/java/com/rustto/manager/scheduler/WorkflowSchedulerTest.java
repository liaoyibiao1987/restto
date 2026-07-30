package com.rustto.manager.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作流调度器 cron 匹配测试。
 */
class WorkflowSchedulerTest {

    @Test
    void emptyCronNeverDue() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 8, 0);
        assertFalse(WorkflowScheduler.isDue(null, now));
        assertFalse(WorkflowScheduler.isDue("", now));
        assertFalse(WorkflowScheduler.isDue("   ", now));
    }

    @Test
    void invalidCronNeverDue() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 8, 0);
        assertFalse(WorkflowScheduler.isDue("not a cron", now));
    }

    @Test
    void matchesAtSpecificMinute() {
        // 每天 08:00 命中
        LocalDateTime hit = LocalDateTime.of(2026, 7, 28, 8, 0);
        LocalDateTime miss = LocalDateTime.of(2026, 7, 28, 8, 1);
        assertTrue(WorkflowScheduler.isDue("0 0 8 * * *", hit));
        assertFalse(WorkflowScheduler.isDue("0 0 8 * * *", miss));
    }

    @Test
    void weeklyCronMatchesMonday() {
        // 每周一 08:00（周日=0，周一=1）
        LocalDateTime monday = LocalDateTime.of(2026, 7, 27, 8, 0); // 2026-07-27 是周一
        LocalDateTime tuesday = LocalDateTime.of(2026, 7, 28, 8, 0);
        assertTrue(WorkflowScheduler.isDue("0 0 8 ? * MON", monday));
        assertFalse(WorkflowScheduler.isDue("0 0 8 ? * MON", tuesday));
    }

    @Test
    void everyMinuteMatchesAtMinuteBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 8, 0);
        assertTrue(WorkflowScheduler.isDue("0 * * * * *", now));
    }
}
