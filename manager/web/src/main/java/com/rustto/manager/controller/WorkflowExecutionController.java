package com.rustto.manager.controller;

import com.rustto.manager.common.PageResult;
import com.rustto.manager.common.Result;
import com.rustto.manager.dto.ExecutionView;
import com.rustto.manager.entity.WorkflowExecution;
import com.rustto.manager.security.RequirePermission;
import com.rustto.manager.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流执行历史接口。
 */
@RestController
@RequestMapping("/api/workflow-executions")
@RequiredArgsConstructor
@RequirePermission("workflow:execution:list")
public class WorkflowExecutionController {

    private final WorkflowExecutionService workflowExecutionService;

    /**
     * 分页查询执行历史。
     *
     * @param workflowId 工作流 ID（可空表示全部）
     * @param page       页码
     * @param size       每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<WorkflowExecution>> page(@RequestParam(required = false) Long workflowId,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "20") long size) {
        return Result.success(workflowExecutionService.page(workflowId, page, size));
    }

    /**
     * 执行详情（含每步节点结果）。
     *
     * @param id 执行 ID
     * @return 执行详情
     */
    @GetMapping("/{id}")
    @RequirePermission("workflow:execution:query")
    public Result<ExecutionView> get(@PathVariable Long id) {
        return Result.success(workflowExecutionService.getDetail(id));
    }
}
