package com.restto.manager.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流执行详情视图（含每步节点结果）。
 */
@Data
public class ExecutionView {

    /** 执行 ID。 */
    private Long id;

    /** 工作流 ID。 */
    private Long workflowId;

    /** 工作流名称（便于历史展示）。 */
    private String workflowName;

    /** 状态：running / success / failed。 */
    private String status;

    /** 触发类型：manual / schedule。 */
    private String triggerType;

    /** 触发用户 id。 */
    private Long triggeredBy;

    /** 开始时间。 */
    private LocalDateTime startAt;

    /** 结束时间。 */
    private LocalDateTime endAt;

    /** 整体失败原因。 */
    private String errorMsg;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 各节点执行结果。 */
    private List<NodeResult> nodes;

    /** 单节点执行结果。 */
    @Data
    public static class NodeResult {
        /** 节点键。 */
        private String nodeKey;
        /** 节点标签（来自 workflow_node）。 */
        private String label;
        /** 引用 backup_task.id。 */
        private Long taskId;
        /** 状态：waiting / running / success / failed / skipped。 */
        private String status;
        /** 失败原因。 */
        private String errorMsg;
        /** 开始时间。 */
        private LocalDateTime startAt;
        /** 结束时间。 */
        private LocalDateTime endAt;
    }
}
