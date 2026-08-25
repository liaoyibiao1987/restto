package com.restto.manager.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 创建 / 更新工作流请求（整图一次提交，匹配画布「保存」语义）。
 */
@Data
public class WorkflowSaveRequest {

    /** 工作流名称。 */
    @NotBlank(message = "工作流名称不能为空")
    private String name;

    /** 描述。 */
    private String description;

    /** 工作流级 cron（空表示仅手动触发）。 */
    private String cronExpr;

    /** 是否启用。 */
    private Boolean enabled;

    /** 节点列表。 */
    @Valid
    private List<WorkflowNodeDto> nodes;

    /** 边列表。 */
    @Valid
    private List<WorkflowEdgeDto> edges;
}
