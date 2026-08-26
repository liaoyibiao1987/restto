package com.restto.manager.service.backup.workflow;

import com.restto.manager.common.PageResult;
import com.restto.manager.dto.backup.workflow.ExecutionView;
import com.restto.manager.entity.backup.workflow.WorkflowExecution;

/**
 * 工作流执行引擎：编排 DAG、关联 Netty 结果、超时回收。
 *
 * <p>所有可变状态迁移在单线程引擎执行器上串行化（无锁）；Netty / 调度 / 控制器线程仅投递任务。
 */
public interface WorkflowExecutionService {

    /**
     * 启动一次工作流执行。
     *
     * @param workflowId  工作流 ID
     * @param triggerType manual / schedule
     * @param triggeredBy 触发用户 id（调度触发为 null）
     * @return 执行 ID
     */
    Long run(Long workflowId, String triggerType, Long triggeredBy);

    /**
     * Netty 任务结果回调：关联到在途的工作流节点并推进 DAG。
     *
     * <p>无工作流等待该 taskId 时为廉价 no-op。
     *
     * @param taskId 任务 ID
     * @param status success / failed
     * @param error  错误信息（失败时）
     */
    void onTaskResult(Long taskId, String status, String error);

    /**
     * 节点连接断开回调：把该节点所有在途任务标记失败（防止永久挂起）。
     *
     * @param nodeId 节点 ID
     */
    void onNodeDisconnected(Long nodeId);

    /**
     * 分页查询执行历史。
     *
     * @param workflowId 工作流 ID（可空表示全部）
     * @param page       页码
     * @param size       每页大小
     * @return 分页结果
     */
    PageResult<WorkflowExecution> page(Long workflowId, long page, long size);

    /**
     * 执行详情（含每步节点结果）。
     *
     * @param executionId 执行 ID
     * @return 执行详情视图
     */
    ExecutionView getDetail(Long executionId);
}
