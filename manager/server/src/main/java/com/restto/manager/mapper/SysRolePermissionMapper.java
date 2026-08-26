package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.system.role.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限 Mapper（复合主键，service 层用 QueryWrapper）。
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
}
