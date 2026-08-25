package com.rustto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rustto.manager.entity.BackupRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 备份记录 Mapper。
 */
@Mapper
public interface BackupRecordMapper extends BaseMapper<BackupRecord> {
}
