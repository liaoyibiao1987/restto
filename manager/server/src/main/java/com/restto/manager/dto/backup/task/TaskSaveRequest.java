package com.restto.manager.dto.backup.task;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建 / 更新备份任务请求。
 */
@Data
public class TaskSaveRequest {

    /** 任务名称。 */
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /** 目标节点 ID。 */
    @NotNull(message = "节点不能为空")
    private Long nodeId;

    /** 模块名（backup_file / backup_mysql）。 */
    @NotBlank(message = "模块不能为空")
    private String module;

    /** 模块参数 JSON 字符串。 */
    private String paramsJson;

    /** cron 表达式（可空表示仅手动触发）。 */
    private String cronExpr;

    /** 是否启用。 */
    private Boolean enabled;
}
