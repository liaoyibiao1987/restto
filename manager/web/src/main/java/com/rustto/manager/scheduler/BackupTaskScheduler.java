package com.rustto.manager.scheduler;

import com.rustto.manager.entity.BackupTask;
import com.rustto.manager.service.BackupTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 备份任务调度器：每分钟扫描启用任务，cron 命中当前分钟则下发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupTaskScheduler {

    private final BackupTaskService backupTaskService;

    /**
     * 每分钟扫描一次（固定延迟）。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void scan() {
        List<BackupTask> tasks;
        try {
            tasks = backupTaskService.listEnabled();
        } catch (Exception e) {
            log.error("load enabled tasks failed: {}", e.getMessage(), e);
            return;
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        for (BackupTask task : tasks) {
            if (isDue(task.getCronExpr(), now)) {
                try {
                    boolean ok = backupTaskService.runNow(task.getId());
                    log.info("scheduled task #{} ({}) dispatched={}", task.getId(), task.getName(), ok);
                } catch (Exception e) {
                    log.error("dispatch task #{} failed: {}", task.getId(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * cron 是否命中给定时刻（对齐到分钟）。
     *
     * <p>用 {@code next(now-1min)} 得到当前分钟起最近一次触发，比较到分钟粒度。
     *
     * @param cronExpr cron 表达式
     * @param now      当前时刻（已对齐到分钟）
     * @return 命中返回 true；表达式为空或非法返回 false
     */
    static boolean isDue(String cronExpr, LocalDateTime now) {
        if (cronExpr == null || cronExpr.trim().isEmpty()) {
            return false;
        }
        try {
            CronExpression cron = CronExpression.parse(cronExpr);
            LocalDateTime nextFire = cron.next(now.minusMinutes(1));
            if (nextFire == null) {
                return false;
            }
            return nextFire.truncatedTo(ChronoUnit.MINUTES).equals(now);
        } catch (IllegalArgumentException e) {
            log.warn("invalid cron '{}': {}", cronExpr, e.getMessage());
            return false;
        }
    }
}
