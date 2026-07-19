package com.rustto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端二进制版本实体（对应 client_binary 表）。
 */
@Data
@TableName("client_binary")
public class ClientBinary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String version;

    private String filePath;

    private String checksum;

    private Long size;

    private LocalDateTime uploadedAt;
}
