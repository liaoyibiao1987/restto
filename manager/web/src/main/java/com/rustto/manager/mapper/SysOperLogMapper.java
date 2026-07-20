package com.rustto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rustto.manager.entity.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
