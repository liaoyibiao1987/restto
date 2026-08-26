package com.restto.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restto.manager.common.PageResult;
import com.restto.manager.entity.BackupNode;

/**
 * 节点服务。
 */
public interface NodeService extends IService<BackupNode> {

    /**
     * 创建节点（生成随机 Token）。
     *
     * @param nodeName 节点名称
     * @return 节点（含明文 Token，仅此一次返回）
     */
    BackupNode createNode(String nodeName);

    /**
     * 按节点名查找。
     *
     * @param nodeName 节点名称
     * @return 节点，不存在返回 null
     */
    BackupNode findByNodeName(String nodeName);

    /**
     * 刷新心跳时间并置在线。
     *
     * @param nodeId 节点 ID
     */
    void updateHeartbeat(Long nodeId);

    /**
     * 标记离线。
     *
     * @param nodeId 节点 ID
     */
    void markOffline(Long nodeId);

    /**
     * 分页查询。
     *
     * @param page 页码（1 起）
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<BackupNode> page(long page, long size);
}
