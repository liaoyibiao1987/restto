package com.rustto.manager.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 工作流边（编辑器保存时提交，依赖关系 + 条件）。
 */
@Data
public class WorkflowEdgeDto {

    /** 源节点键。 */
    @NotBlank(message = "源节点不能为空")
    private String sourceKey;

    /** 目标节点键。 */
    @NotBlank(message = "目标节点不能为空")
    private String targetKey;

    /** 条件：on_success / on_failed / always（空按 always）。 */
    private String condition;
}
