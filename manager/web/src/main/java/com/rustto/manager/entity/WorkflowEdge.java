package com.rustto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流边实体（对应 workflow_edge 表，依赖关系 + 条件）。
 */
@Data
@TableName("workflow_edge")
public class WorkflowEdge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    private String sourceKey;

    private String targetKey;

    /** on_success / on_failed / always。 */
    private String edgeCondition;

    private LocalDateTime createdAt;
}
