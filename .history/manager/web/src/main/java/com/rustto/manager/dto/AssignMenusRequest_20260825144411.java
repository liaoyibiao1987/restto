package com.restto.manager.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 给角色分配菜单请求。
 */
@Data
public class AssignMenusRequest {

    @NotNull(message = "菜单列表不能为 null")
    private List<Long> menuIds;
}
