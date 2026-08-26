package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.system.role.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色 Mapper。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 判断给定用户是否绑定 {@code admin} 角色（且角色启用）。
     *
     * <p>参数化查询，避免字符串拼接带来的注入风险。
     *
     * @param userId 用户 ID
     * @return 命中条数（&gt;0 即为超管）
     */
    int existsAdminForUser(@Param("userId") Long userId);

    /**
     * 取用户的全部角色编码（已启用角色）。
     *
     * @param userId 用户 ID
     * @return 角色编码集合
     */
    java.util.List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
