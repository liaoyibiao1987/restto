package com.restto.manager.entity.system.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联（复合主键）。
 *
 * <p>注意：复合主键实体不支持 {@code selectById/updateById}，service 层一律使用 {@code QueryWrapper}。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private Long roleId;
}
