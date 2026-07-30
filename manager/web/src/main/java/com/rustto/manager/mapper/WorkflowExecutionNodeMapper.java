package com.rustto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rustto.manager.entity.WorkflowExecutionNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流执行节点 Mapper。
 */
@Mapper
public interface WorkflowExecutionNodeMapper extends BaseMapper<WorkflowExecutionNode> {
}
