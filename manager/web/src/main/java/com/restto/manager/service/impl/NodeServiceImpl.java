package com.restto.manager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.restto.manager.common.BusinessException;
import com.restto.manager.common.PageResult;
import com.restto.manager.common.ResultCode;
import com.restto.manager.entity.BackupNode;
import com.restto.manager.mapper.BackupNodeMapper;
import com.restto.manager.service.NodeService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 节点服务实现。
 */
@Service
public class NodeServiceImpl extends ServiceImpl<BackupNodeMapper, BackupNode> implements NodeService {

    @Override
    public BackupNode createNode(String nodeName) {
        if (findByNodeName(nodeName) != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "节点名已存在: " + nodeName);
        }
        BackupNode node = new BackupNode();
        node.setNodeName(nodeName);
        node.setNodeToken(UUID.randomUUID().toString().replace("-", ""));
        node.setStatus("offline");
        save(node);
        return node;
    }

    @Override
    public BackupNode findByNodeName(String nodeName) {
        return getOne(new QueryWrapper<BackupNode>().eq("node_name", nodeName));
    }

    @Override
    public void updateHeartbeat(Long nodeId) {
        BackupNode node = getById(nodeId);
        if (node == null) {
            return;
        }
        node.setLastHeartbeatAt(LocalDateTime.now());
        node.setStatus("online");
        updateById(node);
    }

    @Override
    public void markOffline(Long nodeId) {
        BackupNode node = getById(nodeId);
        if (node == null) {
            return;
        }
        node.setStatus("offline");
        updateById(node);
    }

    @Override
    public PageResult<BackupNode> page(long page, long size) {
        Page<BackupNode> p = new Page<>(page, size);
        Page<BackupNode> result = page(p,
                new QueryWrapper<BackupNode>().orderByDesc("created_at"));
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }
}
