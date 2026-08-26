package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.backup.node.BackupNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户端节点 Mapper。
 */
@Mapper
public interface BackupNodeMapper extends BaseMapper<BackupNode> {
}
