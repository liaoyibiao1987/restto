package com.restto.manager.controller;

import com.restto.manager.common.BusinessException;
import com.restto.manager.common.PageResult;
import com.restto.manager.common.Result;
import com.restto.manager.common.ResultCode;
import com.restto.manager.dto.NodeCreateRequest;
import com.restto.manager.entity.BackupNode;
import com.restto.manager.security.OperLog;
import com.restto.manager.security.RequirePermission;
import com.restto.manager.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 客户端节点接口。
 */
@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
@RequirePermission("backup:node:list")
public class NodeController {

    private final NodeService nodeService;

    /**
     * 创建节点（返回一次性明文 Token）。
     *
     * @param request 创建请求
     * @return 节点
     */
    @PostMapping
    @RequirePermission("backup:node:create")
    @OperLog("新增节点")
    public Result<BackupNode> create(@RequestBody @Valid NodeCreateRequest request) {
        return Result.success(nodeService.createNode(request.getNodeName()));
    }

    /**
     * 分页查询节点。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<BackupNode>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        return Result.success(nodeService.page(page, size));
    }

    /**
     * 节点详情。
     *
     * @param id 节点 ID
     * @return 节点
     */
    @GetMapping("/{id}")
    @RequirePermission("backup:node:query")
    public Result<BackupNode> get(@PathVariable Long id) {
        BackupNode node = nodeService.getById(id);
        if (node == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "节点不存在");
        }
        return Result.success(node);
    }

    /**
     * 删除节点。
     *
     * @param id 节点 ID
     * @return 结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("backup:node:delete")
    @OperLog("删除节点")
    public Result<Void> delete(@PathVariable Long id) {
        nodeService.removeById(id);
        return Result.success();
    }
}
