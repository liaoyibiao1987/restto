package com.restto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备份执行记录实体（对应 backup_record 表）。
 */
@Data
@TableName("backup_record")
public class BackupRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long nodeId;

    /** pending / success / failed */
    private String status;

    private String filePath;

    private Long size;

    private String checksum;

    private String errorMsg;

    private LocalDateTime startAt;

    private LocalDateTime endAt;
}
