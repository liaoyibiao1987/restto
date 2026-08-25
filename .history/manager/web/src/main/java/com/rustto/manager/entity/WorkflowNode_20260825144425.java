package com.restto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流节点实体（对应 workflow_node 表，引用 backup_task.id）。
 */
@Data
@TableName("workflow_node")
public class WorkflowNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    /** 工作流内唯一节点键（前端生成）。 */
    private String nodeKey;

    private String label;

    /** 引用 backup_task.id。 */
    private Long taskId;

    /** 画布横坐标（前端布局，引擎忽略）。 */
    private Integer posX;

    /** 画布纵坐标（前端布局，引擎忽略）。 */
    private Integer posY;

    private LocalDateTime createdAt;
}
