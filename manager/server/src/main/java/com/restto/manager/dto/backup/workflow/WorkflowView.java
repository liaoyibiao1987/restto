package com.restto.manager.dto.backup.workflow;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流详情视图（含整图：节点 + 边），供编辑器加载与详情接口返回。
 */
@Data
public class WorkflowView {

    /** 工作流 ID。 */
    private Long id;

    /** 名称。 */
    private String name;

    /** 描述。 */
    private String description;

    /** 工作流级 cron。 */
    private String cronExpr;

    /** 是否启用。 */
    private Boolean enabled;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 节点列表。 */
    private List<WorkflowNodeDto> nodes;

    /** 边列表。 */
    private List<WorkflowEdgeDto> edges;
}
