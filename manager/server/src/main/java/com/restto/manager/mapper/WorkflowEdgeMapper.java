package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.backup.workflow.WorkflowEdge;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流边 Mapper。
 */
@Mapper
public interface WorkflowEdgeMapper extends BaseMapper<WorkflowEdge> {
}
