package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-菜单 Mapper（复合主键，service 层用 QueryWrapper）。
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {
}
