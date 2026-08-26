package com.restto.manager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体（对应 sys_oper_log 表，由 {@code @OperLog} AOP 切面写入）。
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String operUser;

    private Long operUserId;

    private String operUri;

    private String operMethod;

    private String requestMethod;

    private String requestParams;

    /** 1 成功 / 0 失败。 */
    private Integer status;

    private String errorMsg;

    private Long costMs;

    private String operIp;

    private LocalDateTime operTime;
}
