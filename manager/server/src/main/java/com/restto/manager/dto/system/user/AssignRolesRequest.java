package com.restto.manager.dto.system.user;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 给用户分配角色请求。
 */
@Data
public class AssignRolesRequest {

    @NotNull(message = "角色列表不能为 null")
    private List<Long> roleIds;
}
