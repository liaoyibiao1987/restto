package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 后台用户 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
