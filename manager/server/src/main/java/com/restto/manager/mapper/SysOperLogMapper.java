package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.system.operlog.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
