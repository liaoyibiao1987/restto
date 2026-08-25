package com.restto.manager.controller;

import com.restto.manager.common.PageResult;
import com.restto.manager.common.Result;
import com.restto.manager.dto.WorkflowSaveRequest;
import com.restto.manager.dto.WorkflowView;
import com.restto.manager.entity.Workflow;
import com.restto.manager.security.OperLog;
import com.restto.manager.security.RequirePermission;
import com.restto.manager.security.UserContext;
import com.restto.manager.service.WorkflowExecutionService;
import com.restto.manager.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 工作流定义接口。
 */
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@RequirePermission("workflow:workflow:list")
public class WorkflowController {

    private final WorkflowService workflowService;

    private final WorkflowExecutionService workflowExecutionService;

    /**
     * 创建工作流（含整图）。
     *
     * @param request 保存请求
     * @return 工作流详情
     */
    @PostMapping
    @RequirePermission("workflow:workflow:create")
    @OperLog("新增工作流")
    public Result<WorkflowView> create(@RequestBody @Valid WorkflowSaveRequest request) {
        return Result.success(workflowService.create(request));
    }

    /**
     * 更新工作流（整图替换）。
     *
     * @param id      工作流 ID
     * @param request 保存请求
     * @return 工作流详情
     */
    @PutMapping("/{id}")
    @RequirePermission("workflow:workflow:update")
    @OperLog("修改工作流")
    public Result<WorkflowView> update(@PathVariable Long id, @RequestBody @Valid WorkflowSaveRequest request) {
        return Result.success(workflowService.updateGraph(id, request));
    }

    /**
     * 分页查询工作流。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<Workflow>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long size) {
        return Result.success(workflowService.page(page, size));
    }

    /**
     * 工作流详情（含整图）。
     *
     * @param id 工作流 ID
     * @return 工作流详情
     */
    @GetMapping("/{id}")
    @RequirePermission("workflow:workflow:query")
    public Result<WorkflowView> get(@PathVariable Long id) {
        return Result.success(workflowService.getFull(id));
    }

    /**
     * 删除工作流（级联删节点/边，保留执行历史）。
     *
     * @param id 工作流 ID
     * @return 结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("workflow:workflow:delete")
    @OperLog("删除工作流")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.removeFull(id);
        return Result.success();
    }

    /**
     * 立即运行一次工作流。
     *
     * @param id 工作流 ID
     * @return 执行 ID
     */
    @PostMapping("/{id}/run")
    @RequirePermission("workflow:workflow:run")
    @OperLog("运行工作流")
    public Result<Long> run(@PathVariable Long id) {
        Long triggeredBy = UserContext.get() == null ? null : UserContext.get().userId;
        return Result.success(workflowExecutionService.run(id, "manual", triggeredBy));
    }
}
