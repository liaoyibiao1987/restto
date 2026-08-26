package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.system.user.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色 Mapper（复合主键，service 层用 QueryWrapper）。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
