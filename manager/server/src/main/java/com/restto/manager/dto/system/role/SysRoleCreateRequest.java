package com.restto.manager.dto.system.role;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 新建角色请求。
 */
@Data
public class SysRoleCreateRequest {

    @NotBlank(message = "角色编码不能为空")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    private String remark;
}
