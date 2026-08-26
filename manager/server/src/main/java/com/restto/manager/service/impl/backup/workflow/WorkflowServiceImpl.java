package com.restto.manager.service.impl.backup.workflow;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.restto.manager.common.BusinessException;
import com.restto.manager.common.PageResult;
import com.restto.manager.common.ResultCode;
import com.restto.manager.dto.backup.workflow.WorkflowEdgeDto;
import com.restto.manager.dto.backup.workflow.WorkflowNodeDto;
import com.restto.manager.dto.backup.workflow.WorkflowSaveRequest;
import com.restto.manager.dto.backup.workflow.WorkflowView;
import com.restto.manager.entity.backup.task.BackupTask;
import com.restto.manager.entity.backup.workflow.Workflow;
import com.restto.manager.entity.backup.workflow.WorkflowEdge;
import com.restto.manager.entity.backup.workflow.WorkflowNode;
import com.restto.manager.mapper.BackupTaskMapper;
import com.restto.manager.mapper.WorkflowEdgeMapper;
import com.restto.manager.mapper.WorkflowMapper;
import com.restto.manager.mapper.WorkflowNodeMapper;
import com.restto.manager.service.backup.workflow.WorkflowService;
import com.restto.manager.support.WorkflowGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作流定义服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl extends ServiceImpl<WorkflowMapper, Workflow>
        implements WorkflowService {

    private final WorkflowNodeMapper workflowNodeMapper;

    private final WorkflowEdgeMapper workflowEdgeMapper;

    private final BackupTaskMapper backupTaskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowView create(WorkflowSaveRequest request) {
        WorkflowGraph graph = validate(request);
        Workflow wf = new Workflow();
        wf.setName(request.getName());
        wf.setDescription(request.getDescription());
        wf.setCronExpr(request.getCronExpr());
        wf.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        save(wf);
        replaceGraph(wf.getId(), request);
        log.info("workflow created id={} nodes={} edges={}", wf.getId(),
                graph.nodeKeys().size(), request.getEdges() == null ? 0 : request.getEdges().size());
        return getFull(wf.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowView updateGraph(Long id, WorkflowSaveRequest request) {
        Workflow wf = getById(id);
        if (wf == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        validate(request);
        wf.setName(request.getName());
        wf.setDescription(request.getDescription());
        wf.setCronExpr(request.getCronExpr());
        if (request.getEnabled() != null) {
            wf.setEnabled(request.getEnabled());
        }
        updateById(wf);
        replaceGraph(id, request);
        return getFull(id);
    }

    @Override
    public WorkflowView getFull(Long id) {
        Workflow wf = getById(id);
        if (wf == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        return assemble(wf);
    }

    @Override
    public PageResult<Workflow> page(long page, long size) {
        Page<Workflow> result = page(new Page<>(page, size),
                new QueryWrapper<Workflow>().orderByDesc("updated_at"));
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFull(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工作流不存在");
        }
        workflowNodeMapper.delete(new QueryWrapper<WorkflowNode>().eq("workflow_id", id));
        workflowEdgeMapper.delete(new QueryWrapper<WorkflowEdge>().eq("workflow_id", id));
        removeById(id);
        // 执行历史（workflow_execution / workflow_execution_node）保留，便于审计。
    }

    @Override
    public List<Workflow> listEnabled() {
        return list(new QueryWrapper<Workflow>().eq("enabled", 1));
    }

    /**
     * 校验请求：task_id 存在性 + 图合法性与无环。
     *
     * @param request 保存请求
     * @return 已校验的图
     */
    private WorkflowGraph validate(WorkflowSaveRequest request) {
        List<WorkflowNodeDto> nodes = request.getNodes() == null ? new ArrayList<>() : request.getNodes();
        // task_id 存在性软校验
        Set<Long> taskIds = nodes.stream().map(WorkflowNodeDto::getTaskId).collect(Collectors.toSet());
        if (!taskIds.isEmpty()) {
            List<BackupTask> found = backupTaskMapper.selectBatchIds(taskIds);
            if (found.size() != taskIds.size()) {
                Set<Long> foundIds = found.stream().map(BackupTask::getId).collect(Collectors.toSet());
                taskIds.removeAll(foundIds);
                throw new BusinessException(ResultCode.PARAM_INVALID, "引用了不存在的任务: " + taskIds);
            }
        }
        // 节点键唯一性
        Set<String> seen = new HashSet<>();
        for (WorkflowNodeDto n : nodes) {
            if (!seen.add(n.getNodeKey())) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "节点键重复: " + n.getNodeKey());
            }
        }
        WorkflowGraph graph = toGraph(nodes, request.getEdges());
        graph.validateWellFormed();
        graph.validateAcyclic();
        return graph;
    }

    /**
     * DTO → 纯引擎图。
     *
     * @param nodes 节点 DTO
     * @param edges 边 DTO
     * @return 工作流图
     */
    private WorkflowGraph toGraph(List<WorkflowNodeDto> nodes, List<WorkflowEdgeDto> edges) {
        Set<String> keys = nodes.stream().map(WorkflowNodeDto::getNodeKey).collect(Collectors.toSet());
        List<WorkflowGraph.Edge> es = new ArrayList<>();
        if (edges != null) {
            for (WorkflowEdgeDto d : edges) {
                es.add(new WorkflowGraph.Edge(d.getSourceKey(), d.getTargetKey(), d.getCondition()));
            }
        }
        return WorkflowGraph.build(keys, es);
    }

    /**
     * 整图替换：删旧节点/边再插新的。
     *
     * @param workflowId 工作流 ID
     * @param request    保存请求
     */
    private void replaceGraph(Long workflowId, WorkflowSaveRequest request) {
        workflowNodeMapper.delete(new QueryWrapper<WorkflowNode>().eq("workflow_id", workflowId));
        workflowEdgeMapper.delete(new QueryWrapper<WorkflowEdge>().eq("workflow_id", workflowId));
        if (request.getNodes() != null) {
            for (WorkflowNodeDto d : request.getNodes()) {
                WorkflowNode n = new WorkflowNode();
                n.setWorkflowId(workflowId);
                n.setNodeKey(d.getNodeKey());
                n.setLabel(d.getLabel());
                n.setTaskId(d.getTaskId());
                n.setPosX(d.getPosX());
                n.setPosY(d.getPosY());
                workflowNodeMapper.insert(n);
            }
        }
        if (request.getEdges() != null) {
            for (WorkflowEdgeDto d : request.getEdges()) {
                WorkflowEdge e = new WorkflowEdge();
                e.setWorkflowId(workflowId);
                e.setSourceKey(d.getSourceKey());
                e.setTargetKey(d.getTargetKey());
                e.setEdgeCondition(WorkflowGraph.normalize(d.getCondition()));
                workflowEdgeMapper.insert(e);
            }
        }
    }

    /**
     * 组装工作流详情视图（含节点 + 边）。
     *
     * @param wf 工作流
     * @return 详情视图
     */
    private WorkflowView assemble(Workflow wf) {
        WorkflowView view = new WorkflowView();
        view.setId(wf.getId());
        view.setName(wf.getName());
        view.setDescription(wf.getDescription());
        view.setCronExpr(wf.getCronExpr());
        view.setEnabled(wf.getEnabled());
        view.setCreatedAt(wf.getCreatedAt());
        view.setUpdatedAt(wf.getUpdatedAt());

        List<WorkflowNode> nodes = workflowNodeMapper.selectList(
                new QueryWrapper<WorkflowNode>().eq("workflow_id", wf.getId()).orderByAsc("id"));
        List<WorkflowNodeDto> nodeDtos = nodes.stream().map(n -> {
            WorkflowNodeDto d = new WorkflowNodeDto();
            d.setNodeKey(n.getNodeKey());
            d.setLabel(n.getLabel());
            d.setTaskId(n.getTaskId());
            d.setPosX(n.getPosX());
            d.setPosY(n.getPosY());
            return d;
        }).collect(Collectors.toList());
        view.setNodes(nodeDtos);

        List<WorkflowEdge> edges = workflowEdgeMapper.selectList(
                new QueryWrapper<WorkflowEdge>().eq("workflow_id", wf.getId()).orderByAsc("id"));
        List<WorkflowEdgeDto> edgeDtos = edges.stream().map(e -> {
            WorkflowEdgeDto d = new WorkflowEdgeDto();
            d.setSourceKey(e.getSourceKey());
            d.setTargetKey(e.getTargetKey());
            d.setCondition(e.getEdgeCondition());
            return d;
        }).collect(Collectors.toList());
        view.setEdges(edgeDtos);

        return view;
    }
}
