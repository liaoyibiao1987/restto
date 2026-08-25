package com.restto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备份任务实体（对应 backup_task 表）。
 */
@Data
@TableName("backup_task")
public class BackupTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long nodeId;

    /** backup_file / backup_mysql */
    private String module;

    private String paramsJson;

    private String cronExpr;

    private Boolean enabled;

    private LocalDateTime createdAt;
}
