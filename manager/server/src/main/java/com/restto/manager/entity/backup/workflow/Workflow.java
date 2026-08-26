package com.restto.manager.entity.backup.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流定义实体（对应 workflow 表）。
 */
@Data
@TableName("workflow")
public class Workflow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** 工作流级 cron；空表示仅手动触发。 */
    private String cronExpr;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
