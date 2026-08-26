package com.restto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流执行节点实体（对应 workflow_execution_node 表，每步结果）。
 */
@Data
@TableName("workflow_execution_node")
public class WorkflowExecutionNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long executionId;

    private String nodeKey;

    private Long taskId;

    /** waiting / running / success / failed / skipped。 */
    private String status;

    private String errorMsg;

    private LocalDateTime startAt;

    private LocalDateTime endAt;
}
