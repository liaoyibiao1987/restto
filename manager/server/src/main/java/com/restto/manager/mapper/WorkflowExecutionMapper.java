package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.backup.workflow.WorkflowExecution;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流执行 Mapper。
 */
@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecution> {
}
