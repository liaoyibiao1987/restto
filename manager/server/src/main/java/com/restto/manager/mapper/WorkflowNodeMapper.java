package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.backup.workflow.WorkflowNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流节点 Mapper。
 */
@Mapper
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNode> {
}
