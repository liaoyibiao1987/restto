package com.rustto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端节点实体（对应 backup_node 表）。
 */
@Data
@TableName("backup_node")
public class BackupNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String nodeName;

    private String nodeToken;

    /** offline / online */
    private String status;

    private String version;

    private LocalDateTime lastHeartbeatAt;

    private LocalDateTime createdAt;
}
