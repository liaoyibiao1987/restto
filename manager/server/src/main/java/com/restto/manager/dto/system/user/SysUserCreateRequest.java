package com.restto.manager.dto.system.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 新建用户请求。
 */
@Data
public class SysUserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String nickname;

    private String email;

    /** 创建时一并分配的角色 ID（可空）。 */
    private List<Long> roleIds;
}
