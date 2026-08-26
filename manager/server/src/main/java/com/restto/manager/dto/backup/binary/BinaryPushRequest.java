package com.restto.manager.dto.backup.binary;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 二进制下发请求：把某版本二进制推送到指定节点。
 */
@Data
public class BinaryPushRequest {

    /** 目标节点 ID。 */
    @NotNull(message = "节点不能为空")
    private Long nodeId;
}
