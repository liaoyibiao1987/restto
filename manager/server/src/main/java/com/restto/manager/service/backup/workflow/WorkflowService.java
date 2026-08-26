package com.restto.manager.service.backup.workflow;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restto.manager.common.PageResult;
import com.restto.manager.dto.backup.workflow.WorkflowSaveRequest;
import com.restto.manager.dto.backup.workflow.WorkflowView;
import com.restto.manager.entity.backup.workflow.Workflow;

/**
 * 工作流定义服务（CRUD + 整图保存）。
 */
public interface WorkflowService extends IService<Workflow> {

    /**
     * 创建工作流（含整图）。
     *
     * @param request 保存请求
     * @return 工作流详情
     */
    WorkflowView create(WorkflowSaveRequest request);

    /**
     * 更新工作流（整图替换：删旧节点/边再插新的）。
     *
     * @param id      工作流 ID
     * @param request 保存请求
     * @return 工作流详情
     */
    WorkflowView updateGraph(Long id, WorkflowSaveRequest request);

    /**
     * 工作流详情（含整图）。
     *
     * @param id 工作流 ID
     * @return 工作流详情
     */
    WorkflowView getFull(Long id);

    /**
     * 分页查询。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<Workflow> page(long page, long size);

    /**
     * 级联删除工作流（节点/边；保留执行历史）。
     *
     * @param id 工作流 ID
     */
    void removeFull(Long id);

    /**
     * 取所有启用的工作流（供调度器使用）。
     *
     * @return 启用工作流列表
     */
    java.util.List<Workflow> listEnabled();
}
