package com.restto.manager.scheduler;

import com.restto.manager.entity.Workflow;
import com.restto.manager.service.WorkflowExecutionService;
import com.restto.manager.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 工作流调度器：每分钟扫描启用工作流，cron 命中当前分钟则触发一次执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowScheduler {

    private final WorkflowService workflowService;

    private final WorkflowExecutionService workflowExecutionService;

    /**
     * 每分钟扫描一次（固定延迟）。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 45_000L)
    public void scan() {
        List<Workflow> workflows;
        try {
            workflows = workflowService.listEnabled();
        } catch (Exception e) {
            log.error("load enabled workflows failed: {}", e.getMessage(), e);
            return;
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        for (Workflow wf : workflows) {
            if (isDue(wf.getCronExpr(), now)) {
                try {
                    Long execId = workflowExecutionService.run(wf.getId(), "schedule", null);
                    log.info("scheduled workflow #{} ({}) execution #{}", wf.getId(), wf.getName(), execId);
                } catch (Exception e) {
                    log.error("schedule workflow #{} failed: {}", wf.getId(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * cron 是否命中给定时刻（对齐到分钟）。
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
            log.warn("invalid workflow cron '{}': {}", cronExpr, e.getMessage());
            return false;
        }
    }
}
