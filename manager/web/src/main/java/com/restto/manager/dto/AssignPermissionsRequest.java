package com.restto.manager.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 给角色分配权限请求。
 */
@Data
public class AssignPermissionsRequest {

    @NotNull(message = "权限列表不能为 null")
    private List<Long> permissionIds;
}
