package com.restto.manager.entity.backup.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流执行实体（对应 workflow_execution 表，一次运行）。
 */
@Data
@TableName("workflow_execution")
public class WorkflowExecution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    /** running / success / failed。 */
    private String status;

    /** manual / schedule。 */
    private String triggerType;

    /** 触发用户 id（审计）。 */
    private Long triggeredBy;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private String errorMsg;

    private LocalDateTime createdAt;
}
