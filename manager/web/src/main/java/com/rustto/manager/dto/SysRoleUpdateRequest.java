package com.rustto.manager.dto;

import lombok.Data;

/**
 * 修改角色请求（role_code 创建后不可改）。
 */
@Data
public class SysRoleUpdateRequest {

    private String roleName;

    /** 0 停用 / 1 启用。 */
    private Integer status;

    private String remark;
}
