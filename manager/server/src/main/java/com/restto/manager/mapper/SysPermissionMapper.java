package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限点 Mapper。
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 取用户的全部权限码（经 user_role→role_permission→permission 关联，过滤启用角色），去重。
     *
     * @param userId 用户 ID
     * @return 权限码列表
     */
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}
