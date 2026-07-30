package com.rustto.manager.service.impl;

import com.rustto.manager.common.BusinessException;
import com.rustto.manager.common.ResultCode;
import com.rustto.manager.entity.Workflow;
import com.rustto.manager.entity.WorkflowEdge;
import com.rustto.manager.entity.WorkflowExecution;
import com.rustto.manager.entity.WorkflowNode;
import com.rustto.manager.mapper.BackupTaskMapper;
import com.rustto.manager.mapper.WorkflowEdgeMapper;
import com.rustto.manager.mapper.WorkflowExecutionMapper;
import com.rustto.manager.mapper.WorkflowExecutionNodeMapper;
import com.rustto.manager.mapper.WorkflowMapper;
import com.rustto.manager.mapper.WorkflowNodeMapper;
import com.rustto.manager.service.BackupTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作流执行引擎端到端单测（Mockito 模拟 DB 层，驱动真实引擎 / IO 线程）。
 *
 * <p>核心 DAG 逻辑由 {@code WorkflowGraphTest} 覆盖；此处覆盖 dispatch 结果三分支与 finalize 规则。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowExecutionServiceImplTest {

    @Mock
    private WorkflowMapper workflowMapper;

    @Mock
    private WorkflowNodeMapper workflowNodeMapper;

    @Mock
    private WorkflowEdgeMapper workflowEdgeMapper;

    @Mock
    private WorkflowExecutionMapper workflowExecutionMapper;

    @Mock
    private WorkflowExecutionNodeMapper workflowExecutionNodeMapper;

    @Mock
    private BackupTaskMapper backupTaskMapper;

    @Mock
    private BackupTaskService backupTaskService;

    private WorkflowExecutionServiceImpl service;

    /**
     * 构造服务，并让 insert 回填自增 id（模拟 MyBatis 行为）。
     */
    @BeforeEach
    void setUp() {
        service = new WorkflowExecutionServiceImpl(workflowMapper, workflowNodeMapper, workflowEdgeMapper,
                workflowExecutionMapper, workflowExecutionNodeMapper, backupTaskMapper, backupTaskService);
        lenient().doAnswer(inv -> {
            WorkflowExecution e = inv.getArgument(0);
            e.setId(1L);
            return 1;
        }).when(workflowExecutionMapper).insert(any(WorkflowExecution.class));
    }

    /**
     * 关闭线程池，避免泄漏。
     */
    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void runMissingWorkflowThrowsNotFound() {
        when(workflowMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.run(99L, "manual", null));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void dispatchOfflineMarksFailedAndFinalizes() {
        stubSingleEntryGraph(10L);
        when(backupTaskService.runNow(10L)).thenReturn(false);

        Long execId = service.run(7L, "manual", null);
        assertEquals(Long.valueOf(1L), execId);

        verify(backupTaskService, timeout(2000)).runNow(10L);
        verify(workflowExecutionMapper, timeout(3000))
                .updateById(argThat(e -> e != null && "failed".equals(e.getStatus())));
    }

    @Test
    void dispatchThrowsMarksFailedAndFinalizes() {
        stubSingleEntryGraph(10L);
        when(backupTaskService.runNow(10L))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "任务不存在"));

        service.run(7L, "manual", null);

        verify(backupTaskService, timeout(2000)).runNow(10L);
        verify(workflowExecutionMapper, timeout(3000))
                .updateById(argThat(e -> e != null && "failed".equals(e.getStatus())));
    }

    @Test
    void serialChainAdvancesAndFinalizesFailed() {
        // a(task 10) → b(task 20) on_success；a 成功下发后回 success，b 回 failed → 整体失败
        Workflow wf = new Workflow();
        wf.setId(7L);
        when(workflowMapper.selectById(7L)).thenReturn(wf);
        when(workflowNodeMapper.selectList(any()))
                .thenReturn(Arrays.asList(node("a", 10L), node("b", 20L)));
        when(workflowEdgeMapper.selectList(any()))
                .thenReturn(Collections.singletonList(edge("a", "b", "on_success")));
        when(backupTaskService.runNow(10L)).thenReturn(true);
        when(backupTaskService.runNow(20L)).thenReturn(true);

        Long execId = service.run(7L, "manual", 1L);
        assertEquals(Long.valueOf(1L), execId);

        // a 下发
        verify(backupTaskService, timeout(2000)).runNow(10L);
        // a 成功 → 引擎推进，下发 b
        service.onTaskResult(10L, "success", null);
        verify(backupTaskService, timeout(2000)).runNow(20L);
        // b 失败 → 整体 FAILED
        service.onTaskResult(20L, "failed", "boom");
        verify(workflowExecutionMapper, timeout(3000))
                .updateById(argThat(e -> e != null && "failed".equals(e.getStatus())));
    }

    @Test
    void parallelEntriesDispatchedConcurrently() {
        // a、b 均为入口（无入边），应都被下发
        Workflow wf = new Workflow();
        wf.setId(7L);
        when(workflowMapper.selectById(7L)).thenReturn(wf);
        when(workflowNodeMapper.selectList(any()))
                .thenReturn(Arrays.asList(node("a", 10L), node("b", 20L)));
        when(workflowEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(backupTaskService.runNow(10L)).thenReturn(true);
        when(backupTaskService.runNow(20L)).thenReturn(true);

        service.run(7L, "manual", null);

        verify(backupTaskService, timeout(2000)).runNow(10L);
        verify(backupTaskService, timeout(2000)).runNow(20L);
        // 双双成功 → 整体 SUCCESS
        service.onTaskResult(10L, "success", null);
        service.onTaskResult(20L, "success", null);
        verify(workflowExecutionMapper, timeout(3000))
                .updateById(argThat(e -> e != null && "success".equals(e.getStatus())));
    }

    /**
     * 单入口节点图（taskId）。
     *
     * @param taskId 任务 ID
     */
    private void stubSingleEntryGraph(Long taskId) {
        Workflow wf = new Workflow();
        wf.setId(7L);
        when(workflowMapper.selectById(7L)).thenReturn(wf);
        when(workflowNodeMapper.selectList(any())).thenReturn(Collections.singletonList(node("a", taskId)));
        when(workflowEdgeMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    /**
     * @param key    节点键
     * @param taskId 任务 ID
     * @return 节点
     */
    private static WorkflowNode node(String key, Long taskId) {
        WorkflowNode n = new WorkflowNode();
        n.setNodeKey(key);
        n.setTaskId(taskId);
        n.setLabel(key);
        return n;
    }

    /**
     * @param src       源
     * @param tgt       目标
     * @param condition 条件
     * @return 边
     */
    private static WorkflowEdge edge(String src, String tgt, String condition) {
        WorkflowEdge e = new WorkflowEdge();
        e.setSourceKey(src);
        e.setTargetKey(tgt);
        e.setEdgeCondition(condition);
        return e;
    }
}
