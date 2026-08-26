package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.backup.task.BackupTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 备份任务 Mapper。
 */
@Mapper
public interface BackupTaskMapper extends BaseMapper<BackupTask> {
}
