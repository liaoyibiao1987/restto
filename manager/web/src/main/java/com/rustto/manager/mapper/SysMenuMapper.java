package com.rustto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rustto.manager.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 菜单 Mapper。
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 取用户可见的菜单列表（非超管路径：经 user_role→role_menu→menu 关联，过滤 visible/status 与启用角色），按 sort 升序。
     *
     * @param userId 用户 ID
     * @return 菜单列表
     */
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);
}
