package com.rustto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rustto.manager.entity.BackupTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 备份任务 Mapper。
 */
@Mapper
public interface BackupTaskMapper extends BaseMapper<BackupTask> {
}
