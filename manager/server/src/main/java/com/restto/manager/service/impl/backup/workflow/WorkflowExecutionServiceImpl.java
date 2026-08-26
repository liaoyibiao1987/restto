package com.restto.manager.service.impl.backup.workflow;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restto.manager.common.BusinessException;
import com.restto.manager.common.PageResult;
import com.restto.manager.common.ResultCode;
import com.restto.manager.dto.backup.workflow.ExecutionView;
import com.restto.manager.entity.backup.task.BackupTask;
import com.restto.manager.entity.backup.workflow.Workflow;
import com.restto.manager.entity.backup.workflow.WorkflowEdge;
import com.restto.manager.entity.backup.workflow.WorkflowExecution;
import com.restto.manager.entity.backup.workflow.WorkflowExecutionNode;
import com.restto.manager.entity.backup.workflow.WorkflowNode;
import com.restto.manager.mapper.BackupTaskMapper;
import com.restto.manager.mapper.WorkflowEdgeMapper;
import com.restto.manager.mapper.WorkflowExecutionMapper;
import com.restto.manager.mapper.WorkflowExecutionNodeMapper;
import com.restto.manager.mapper.WorkflowMapper;
import com.restto.manager.mapper.WorkflowNodeMapper;
import com.restto.manager.service.backup.task.BackupTaskService;
import com.restto.manager.service.backup.workflow.WorkflowExecutionService;
import com.restto.manager.support.NodeState;
import com.restto.manager.support.WorkflowGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 工作流执行引擎实现。
 *
 * <p>线程模型：
 * <ul>
 *   <li>{@code engineExecutor}（单线程 daemon）：拥有全部 DAG 状态、执行上下文缓存、在途关联表；
 *       所有状态迁移与对应的执行/节点状态落库在此串行化，无锁。</li>
 *   <li>{@code ioExecutor}（有界池）：仅执行 {@link BackupTaskService#runNow}（含 DB + Netty send），
 *       结果以 outcome 回投引擎线程。</li>
 *   <li>Netty / 调度 / 控制器线程：只 {@code engineExecutor.execute(...)}，绝不直接碰引擎状态。</li>
 * </ul>
 *
 * <p>关联：taskId → ArrayDeque&lt;RunningNode&gt;，调度前若非空则驻车；结果到达 pollFirst 唯一条目。
 * 不改 Netty 协议（key 仍为 taskId）。
 *
 * <p>防挂死：dispatch 失败立即标 FAILED；节点断连 / 超时回收器把在途节点标 FAILED。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowExecutionMapper workflowExecutionMapper;
    private final WorkflowExecutionNodeMapper workflowExecutionNodeMapper;
    private final BackupTaskMapper backupTaskMapper;
    private final BackupTaskService backupTaskService;

    /** 在途任务节点（关联键）。 */
    private static final class RunningNode {
        /** 执行 ID。 */
        final long execId;
        /** 节点键。 */
        final String nodeKey;

        RunningNode(long execId, String nodeKey) {
            this.execId = execId;
            this.nodeKey = nodeKey;
        }
    }

    /** 单次执行的内存上下文（仅引擎线程读写）。 */
    private static final class ExecContext {
        /** 执行 ID。 */
        final long executionId;
        /** 工作流 ID。 */
        final long workflowId;
        /** 图。 */
        final WorkflowGraph graph;
        /** 节点键→状态。 */
        final Map<String, NodeState> states;
        /** 节点键→标签。 */
        final Map<String, String> labels;
        /** 节点键→taskId。 */
        final Map<String, Long> taskIds;
        /** 节点键→错误信息。 */
        final Map<String, String> errors;

        ExecContext(long executionId, long workflowId, WorkflowGraph graph,
                    Map<String, NodeState> states, Map<String, String> labels,
                    Map<String, Long> taskIds, Map<String, String> errors) {
            this.executionId = executionId;
            this.workflowId = workflowId;
            this.graph = graph;
            this.states = states;
            this.labels = labels;
            this.taskIds = taskIds;
            this.errors = errors;
        }
    }

    /** 超时阈值（分钟）。 */
    @Value("${restto.workflow.timeout-minutes:30}")
    private int timeoutMinutes;

    /** 引擎线程（单线程串行化所有状态迁移）。 */
    private final ExecutorService engineExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "workflow-engine");
        t.setDaemon(true);
        return t;
    });

    /** IO 线程池（仅跑 runNow；有界队列 + CallerRunsPolicy 降级）。 */
    private final ThreadPoolExecutor ioExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(100),
            r -> {
                Thread t = new Thread(r, "workflow-io");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    /** 活跃执行上下文：executionId → context。 */
    private final Map<Long, ExecContext> activeContexts = new HashMap<>();

    /** 在途关联：taskId → 等待结果的节点队列（引擎线程独占）。 */
    private final Map<Long, Deque<RunningNode>> inflightByTaskId = new HashMap<>();

    /** 在途 taskId 集合（并发可读）：供 Netty 线程做廉价 no-op 判断，避免读非线程安全的 inflightByTaskId。 */
    private final Set<Long> inflightTaskIds = ConcurrentHashMap.newKeySet();

    /**
     * 关闭线程池。
     */
    @PreDestroy
    public void shutdown() {
        engineExecutor.shutdownNow();
        ioExecutor.shutdownNow();
    }

    @Override
    public Long run(Long workflowId, String triggerType, Long triggeredBy) {
        Workflow wf = workflowMapper.selectById(workflowId);
        if (wf == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        GraphBundle bundle = loadGraph(workflowId);
        bundle.graph.validateWellFormed();
        bundle.graph.validateAcyclic();

        WorkflowExecution exec = new WorkflowExecution();
        exec.setWorkflowId(workflowId);
        exec.setStatus("running");
        exec.setTriggerType(triggerType == null ? "manual" : triggerType);
        exec.setTriggeredBy(triggeredBy);
        exec.setStartAt(LocalDateTime.now());
        workflowExecutionMapper.insert(exec);
        long execId = exec.getId();

        // 创建每个节点的 WAITING 记录，并构建内存上下文
        Map<String, NodeState> states = new LinkedHashMap<>();
        for (String key : bundle.graph.nodeKeys()) {
            WorkflowExecutionNode en = new WorkflowExecutionNode();
            en.setExecutionId(execId);
            en.setNodeKey(key);
            en.setTaskId(bundle.taskIds.get(key));
            en.setStatus(stateToString(NodeState.WAITING));
            workflowExecutionNodeMapper.insert(en);
            states.put(key, NodeState.WAITING);
        }
        ExecContext ctx = new ExecContext(execId, workflowId, bundle.graph, states,
                bundle.labels, bundle.taskIds, new HashMap<>());
        activeContexts.put(execId, ctx);

        log.info("workflow #{} execution #{} started (trigger={})", workflowId, execId, exec.getTriggerType());
        engineExecutor.execute(() -> advance(ctx));
        return execId;
    }

    @Override
    public void onTaskResult(Long taskId, String status, String error) {
        // 廉价 no-op：无工作流等待该 taskId 时直接返回，不在 Netty 线程分配 Runnable。
        // 读并发 Set（而非 HashMap），避免与引擎线程的数据竞争。
        if (!inflightTaskIds.contains(taskId)) {
            return;
        }
        engineExecutor.execute(() -> handleTaskResult(taskId, status, error));
    }

    /**
     * 处理任务结果（引擎线程）。
     *
     * @param taskId 任务 ID
     * @param status success / failed
     * @param error  错误信息
     */
    private void handleTaskResult(Long taskId, String status, String error) {
        Deque<RunningNode> dq = inflightByTaskId.get(taskId);
        if (dq == null) {
            return;
        }
        RunningNode r = dq.pollFirst();
        if (r == null) {
            return;
        }
        if (dq.isEmpty()) {
            inflightByTaskId.remove(taskId);
            inflightTaskIds.remove(taskId);
        }
        ExecContext ctx = activeContexts.get(r.execId);
        if (ctx == null) {
            return;
        }
        NodeState ns = "success".equalsIgnoreCase(status) ? NodeState.SUCCESS : NodeState.FAILED;
        applyNodeTerminal(ctx, r.nodeKey, ns, error);
        advance(ctx);
    }

    @Override
    public void onNodeDisconnected(Long nodeId) {
        if (nodeId == null) {
            return;
        }
        // 稀有事件：在调用线程做一次小查询，再交引擎线程处理
        List<BackupTask> tasks = backupTaskMapper.selectList(
                new QueryWrapper<BackupTask>().eq("node_id", nodeId).select("id"));
        if (tasks.isEmpty()) {
            return;
        }
        Set<Long> taskIds = new HashSet<>();
        for (BackupTask t : tasks) {
            taskIds.add(t.getId());
        }
        engineExecutor.execute(() -> failInflightForTasks(taskIds, "node disconnected"));
    }

    /**
     * 把指定 taskId 集合的在途节点全部标 FAILED（引擎线程）。
     *
     * @param taskIds 任务 ID 集合
     * @param reason  失败原因
     */
    private void failInflightForTasks(Set<Long> taskIds, String reason) {
        Set<Long> affected = new HashSet<>();
        for (Long taskId : taskIds) {
            Deque<RunningNode> dq = inflightByTaskId.get(taskId);
            if (dq == null || dq.isEmpty()) {
                continue;
            }
            RunningNode r;
            while ((r = dq.pollFirst()) != null) {
                ExecContext ctx = activeContexts.get(r.execId);
                if (ctx != null) {
                    applyNodeTerminal(ctx, r.nodeKey, NodeState.FAILED, reason);
                    affected.add(r.execId);
                }
            }
            inflightByTaskId.remove(taskId);
            inflightTaskIds.remove(taskId);
        }
        for (Long execId : affected) {
            ExecContext ctx = activeContexts.get(execId);
            if (ctx != null) {
                advance(ctx);
            }
        }
    }

    /**
     * 超时回收：每分钟扫描在途过久 / 卡住的执行（调度线程做查询，引擎线程处理）。
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void reapTimeouts() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<WorkflowExecutionNode> stuck = workflowExecutionNodeMapper.selectList(
                new QueryWrapper<WorkflowExecutionNode>().eq("status", "running").lt("start_at", cutoff));
        List<WorkflowExecution> staleExec = workflowExecutionMapper.selectList(
                new QueryWrapper<WorkflowExecution>().eq("status", "running").lt("start_at", cutoff));
        if (stuck.isEmpty() && staleExec.isEmpty()) {
            return;
        }
        engineExecutor.execute(() -> reapOnEngine(stuck, staleExec));
    }

    /**
     * 引擎线程执行回收。
     *
     * @param stuck    超时的执行节点
     * @param staleExec 卡住的执行
     */
    private void reapOnEngine(List<WorkflowExecutionNode> stuck, List<WorkflowExecution> staleExec) {
        Set<Long> affected = new HashSet<>();
        for (WorkflowExecutionNode en : stuck) {
            ExecContext ctx = activeContexts.get(en.getExecutionId());
            if (ctx == null) {
                ctx = loadContext(en.getExecutionId());
            }
            if (ctx == null) {
                continue;
            }
            if (ctx.states.get(en.getNodeKey()) == NodeState.RUNNING) {
                Long taskId = ctx.taskIds.get(en.getNodeKey());
                removeInflight(taskId, en.getExecutionId(), en.getNodeKey());
                applyNodeTerminal(ctx, en.getNodeKey(), NodeState.FAILED, "timeout: no result from agent");
            }
            affected.add(en.getExecutionId());
        }
        for (WorkflowExecution ex : staleExec) {
            affected.add(ex.getId());
        }
        for (Long execId : affected) {
            ExecContext ctx = activeContexts.get(execId);
            if (ctx == null) {
                ctx = loadContext(execId);
            }
            if (ctx != null) {
                advance(ctx);
            }
        }
    }

    /**
     * 推进 DAG 到不动点（引擎线程）：跳过死节点、下发就绪节点、必要时结算。
     *
     * @param ctx 执行上下文
     */
    private void advance(ExecContext ctx) {
        try {
            // 迭代到不动点
            boolean changed = true;
            while (changed) {
                changed = false;
                // ① 跳过死节点
                for (String key : ctx.graph.deadNodes(ctx.states)) {
                    ctx.states.put(key, NodeState.SKIPPED);
                    persistNodeStatus(ctx.executionId, key, NodeState.SKIPPED, null, null, LocalDateTime.now());
                    changed = true;
                }
                // ② 下发就绪节点（taskId 在途则驻车，保持 WAITING）
                for (String key : ctx.graph.nextReady(ctx.states)) {
                    Long taskId = ctx.taskIds.get(key);
                    Deque<RunningNode> dq = inflightByTaskId.get(taskId);
                    if (dq != null && !dq.isEmpty()) {
                        continue; // 驻车：同一 taskId 已在途，结果回来后再评
                    }
                    ctx.states.put(key, NodeState.RUNNING);
                    ctx.errors.remove(key);
                    persistNodeStatus(ctx.executionId, key, NodeState.RUNNING, null, LocalDateTime.now(), null);
                    inflightByTaskId.computeIfAbsent(taskId, k -> new ArrayDeque<>())
                            .addLast(new RunningNode(ctx.executionId, key));
                    inflightTaskIds.add(taskId);
                    final String nodeKey = key;
                    final Long tid = taskId;
                    ioExecutor.execute(() -> dispatch(ctx.executionId, nodeKey, tid));
                    changed = true;
                }
            }
            // ③ 全终态且无在途 → 结算
            if (!WorkflowGraph.hasRunning(ctx.states) && WorkflowGraph.allTerminal(ctx.states)) {
                finalize(ctx);
            }
        } catch (Exception e) {
            log.error("advance execution #{} failed: {}", ctx.executionId, e.getMessage(), e);
        }
    }

    /**
     * 下发任务（IO 线程）：调 runNow，把 outcome 回投引擎线程。
     *
     * @param execId  执行 ID
     * @param nodeKey 节点键
     * @param taskId  任务 ID
     */
    private void dispatch(long execId, String nodeKey, Long taskId) {
        String failure = null;
        try {
            boolean ok = backupTaskService.runNow(taskId);
            if (!ok) {
                failure = "node offline: 任务下发失败";
            }
        } catch (BusinessException e) {
            failure = e.getMessage();
        } catch (Exception e) {
            failure = "dispatch error: " + e.getMessage();
        }
        if (failure == null) {
            return; // 已下发，等待 Netty 结果
        }
        final String msg = failure;
        engineExecutor.execute(() -> onDispatchFailed(execId, nodeKey, taskId, msg));
    }

    /**
     * 下发失败处理（引擎线程）：标 FAILED 并推进。
     *
     * @param execId  执行 ID
     * @param nodeKey 节点键
     * @param taskId  任务 ID
     * @param reason  失败原因
     */
    private void onDispatchFailed(long execId, String nodeKey, Long taskId, String reason) {
        ExecContext ctx = activeContexts.get(execId);
        if (ctx == null || ctx.states.get(nodeKey) != NodeState.RUNNING) {
            return;
        }
        removeInflight(taskId, execId, nodeKey);
        applyNodeTerminal(ctx, nodeKey, NodeState.FAILED, reason);
        advance(ctx);
    }

    /**
     * 标记节点终态并落库（引擎线程）。
     *
     * @param ctx     上下文
     * @param nodeKey 节点键
     * @param state   终态
     * @param error   错误信息
     */
    private void applyNodeTerminal(ExecContext ctx, String nodeKey, NodeState state, String error) {
        ctx.states.put(nodeKey, state);
        if (error != null) {
            ctx.errors.put(nodeKey, error);
        }
        persistNodeStatus(ctx.executionId, nodeKey, state, error, null, LocalDateTime.now());
    }

    /**
     * 从在途表移除指定条目（引擎线程）。
     *
     * @param taskId  任务 ID
     * @param execId  执行 ID
     * @param nodeKey 节点键
     */
    private void removeInflight(Long taskId, long execId, String nodeKey) {
        Deque<RunningNode> dq = inflightByTaskId.get(taskId);
        if (dq == null) {
            return;
        }
        dq.removeIf(r -> r.execId == execId && r.nodeKey.equals(nodeKey));
        if (dq.isEmpty()) {
            inflightByTaskId.remove(taskId);
            inflightTaskIds.remove(taskId);
        }
    }

    /**
     * 结算执行：写终态、清理上下文（引擎线程）。
     *
     * @param ctx 上下文
     */
    private void finalize(ExecContext ctx) {
        NodeState finalState = WorkflowGraph.finalStatus(ctx.states);
        String summary = WorkflowGraph.firstFailureSummary(ctx.states, ctx.labels, ctx.errors);
        String status = stateToString(finalState);
        WorkflowExecution upd = new WorkflowExecution();
        upd.setId(ctx.executionId);
        upd.setStatus(status);
        upd.setEndAt(LocalDateTime.now());
        upd.setErrorMsg(summary);
        workflowExecutionMapper.updateById(upd);
        // 清理本执行残留的在途条目
        for (String key : ctx.taskIds.keySet()) {
            removeInflight(ctx.taskIds.get(key), ctx.executionId, key);
        }
        activeContexts.remove(ctx.executionId);
        log.info("workflow #{} execution #{} finalized: {}", ctx.workflowId, ctx.executionId, status,
                summary == null ? "" : " (" + summary + ")");
    }

    /**
     * 从 DB 重建执行上下文（重启恢复 / 回收路径用，引擎线程）。
     *
     * @param executionId 执行 ID
     * @return 上下文；执行不存在或非 running 返回 null
     */
    private ExecContext loadContext(Long executionId) {
        WorkflowExecution exec = workflowExecutionMapper.selectById(executionId);
        if (exec == null || !"running".equals(exec.getStatus())) {
            return null;
        }
        GraphBundle bundle = loadGraph(exec.getWorkflowId());
        Map<String, NodeState> states = new LinkedHashMap<>();
        Map<String, String> errors = new HashMap<>();
        List<WorkflowExecutionNode> rows = workflowExecutionNodeMapper.selectList(
                new QueryWrapper<WorkflowExecutionNode>().eq("execution_id", executionId));
        for (WorkflowExecutionNode en : rows) {
            NodeState s = stringToState(en.getStatus());
            states.put(en.getNodeKey(), s);
            if (en.getErrorMsg() != null) {
                errors.put(en.getNodeKey(), en.getErrorMsg());
            }
        }
        ExecContext ctx = new ExecContext(executionId, exec.getWorkflowId(), bundle.graph,
                states, bundle.labels, bundle.taskIds, errors);
        activeContexts.put(executionId, ctx);
        return ctx;
    }

    @Override
    public PageResult<WorkflowExecution> page(Long workflowId, long page, long size) {
        QueryWrapper<WorkflowExecution> qw = new QueryWrapper<WorkflowExecution>().orderByDesc("id");
        if (workflowId != null) {
            qw.eq("workflow_id", workflowId);
        }
        Page<WorkflowExecution> result = workflowExecutionMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }

    @Override
    public ExecutionView getDetail(Long executionId) {
        WorkflowExecution exec = workflowExecutionMapper.selectById(executionId);
        if (exec == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "执行不存在");
        }
        ExecutionView view = new ExecutionView();
        view.setId(exec.getId());
        view.setWorkflowId(exec.getWorkflowId());
        Workflow wf = workflowMapper.selectById(exec.getWorkflowId());
        view.setWorkflowName(wf == null ? null : wf.getName());
        view.setStatus(exec.getStatus());
        view.setTriggerType(exec.getTriggerType());
        view.setTriggeredBy(exec.getTriggeredBy());
        view.setStartAt(exec.getStartAt());
        view.setEndAt(exec.getEndAt());
        view.setErrorMsg(exec.getErrorMsg());
        view.setCreatedAt(exec.getCreatedAt());

        Map<String, String> labels = loadGraph(exec.getWorkflowId()).labels;
        List<WorkflowExecutionNode> rows = workflowExecutionNodeMapper.selectList(
                new QueryWrapper<WorkflowExecutionNode>().eq("execution_id", executionId).orderByAsc("id"));
        List<ExecutionView.NodeResult> results = new ArrayList<>();
        for (WorkflowExecutionNode en : rows) {
            ExecutionView.NodeResult nr = new ExecutionView.NodeResult();
            nr.setNodeKey(en.getNodeKey());
            nr.setLabel(labels.get(en.getNodeKey()));
            nr.setTaskId(en.getTaskId());
            nr.setStatus(en.getStatus());
            nr.setErrorMsg(en.getErrorMsg());
            nr.setStartAt(en.getStartAt());
            nr.setEndAt(en.getEndAt());
            results.add(nr);
        }
        view.setNodes(results);
        return view;
    }

    // ===== 内部工具 =====

    /** 图 + 节点键→taskId + 节点键→标签。 */
    private static final class GraphBundle {
        /** 图。 */
        final WorkflowGraph graph;
        /** 节点键→taskId。 */
        final Map<String, Long> taskIds;
        /** 节点键→标签。 */
        final Map<String, String> labels;

        GraphBundle(WorkflowGraph graph, Map<String, Long> taskIds, Map<String, String> labels) {
            this.graph = graph;
            this.taskIds = taskIds;
            this.labels = labels;
        }
    }

    /**
     * 从 DB 加载工作流图（含 taskId / label 映射）。
     *
     * @param workflowId 工作流 ID
     * @return 图 bundle
     */
    private GraphBundle loadGraph(Long workflowId) {
        List<WorkflowNode> nodes = workflowNodeMapper.selectList(
                new QueryWrapper<WorkflowNode>().eq("workflow_id", workflowId).orderByAsc("id"));
        List<WorkflowEdge> edges = workflowEdgeMapper.selectList(
                new QueryWrapper<WorkflowEdge>().eq("workflow_id", workflowId).orderByAsc("id"));
        Set<String> keys = new HashSet<>();
        Map<String, Long> taskIds = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (WorkflowNode n : nodes) {
            keys.add(n.getNodeKey());
            taskIds.put(n.getNodeKey(), n.getTaskId());
            labels.put(n.getNodeKey(), n.getLabel());
        }
        List<WorkflowGraph.Edge> es = new ArrayList<>();
        for (WorkflowEdge e : edges) {
            es.add(new WorkflowGraph.Edge(e.getSourceKey(), e.getTargetKey(), e.getEdgeCondition()));
        }
        return new GraphBundle(WorkflowGraph.build(keys, es), taskIds, labels);
    }

    /**
     * 持久化节点状态（引擎线程）。
     *
     * @param executionId 执行 ID
     * @param nodeKey     节点键
     * @param state       状态
     * @param error       错误信息（可空）
     * @param startAt     开始时间（可空，仅 RUNNING 设）
     * @param endAt       结束时间（可空，仅终态设）
     */
    private void persistNodeStatus(long executionId, String nodeKey, NodeState state,
                                   String error, LocalDateTime startAt, LocalDateTime endAt) {
        UpdateWrapper<WorkflowExecutionNode> uw = new UpdateWrapper<>();
        uw.eq("execution_id", executionId).eq("node_key", nodeKey)
                .set("status", stateToString(state));
        if (error != null) {
            uw.set("error_msg", error);
        }
        if (startAt != null) {
            uw.set("start_at", startAt);
        }
        if (endAt != null) {
            uw.set("end_at", endAt);
        }
        workflowExecutionNodeMapper.update(null, uw);
    }

    /**
     * @param state 节点状态
     * @return 持久化字符串
     */
    private static String stateToString(NodeState state) {
        switch (state) {
            case WAITING:
                return "waiting";
            case RUNNING:
                return "running";
            case SUCCESS:
                return "success";
            case FAILED:
                return "failed";
            case SKIPPED:
                return "skipped";
            default:
                return "waiting";
        }
    }

    /**
     * @param s 持久化字符串
     * @return 节点状态
     */
    private static NodeState stringToState(String s) {
        if (s == null) {
            return NodeState.WAITING;
        }
        switch (s) {
            case "running":
                return NodeState.RUNNING;
            case "success":
                return NodeState.SUCCESS;
            case "failed":
                return NodeState.FAILED;
            case "skipped":
                return NodeState.SKIPPED;
            default:
                return NodeState.WAITING;
        }
    }
}
