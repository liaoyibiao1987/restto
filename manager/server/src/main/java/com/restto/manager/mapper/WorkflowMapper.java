package com.restto.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restto.manager.entity.backup.workflow.Workflow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工作流 Mapper。
 */
@Mapper
public interface WorkflowMapper extends BaseMapper<Workflow> {
}
