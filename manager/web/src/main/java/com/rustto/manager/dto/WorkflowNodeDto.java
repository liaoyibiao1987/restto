package com.rustto.manager.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 工作流节点（编辑器保存时提交，引用 backup_task）。
 */
@Data
public class WorkflowNodeDto {

    /** 工作流内唯一节点键（前端生成，如 UUID / n1）。 */
    @NotBlank(message = "节点键不能为空")
    private String nodeKey;

    /** 节点显示名。 */
    private String label;

    /** 引用 backup_task.id。 */
    @NotNull(message = "节点任务不能为空")
    private Long taskId;

    /** 画布横坐标。 */
    private Integer posX;

    /** 画布纵坐标。 */
    private Integer posY;
}
