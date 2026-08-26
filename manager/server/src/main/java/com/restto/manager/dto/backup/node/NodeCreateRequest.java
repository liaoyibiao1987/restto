package com.restto.manager.dto.backup.node;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 创建节点请求。
 */
@Data
public class NodeCreateRequest {

    /** 节点名称。 */
    @NotBlank(message = "节点名称不能为空")
    private String nodeName;
}
