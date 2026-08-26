package com.restto.manager.netty.server;

import com.restto.manager.netty.protocol.MessageType;
import com.restto.manager.netty.codec.ProtocolCodec;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 客户端连接注册表：维护 nodeId → Channel。供下发任务 / 二进制使用。
 */
@Slf4j
@Component
public class ConnectionRegistry {

    private final ConcurrentMap<Long, Channel> channels = new ConcurrentHashMap<>();

    /**
     * 注册节点连接。
     *
     * @param nodeId 节点 ID
     * @param channel 通道
     */
    public void register(Long nodeId, Channel channel) {
        Channel old = channels.put(nodeId, channel);
        if (old != null && old != channel && old.isActive()) {
            old.close();
        }
        log.info("node {} connected", nodeId);
    }

    /**
     * 移除节点连接。
     *
     * @param nodeId 节点 ID
     */
    public void remove(Long nodeId) {
        channels.remove(nodeId);
        log.info("node {} disconnected", nodeId);
    }

    /**
     * @param nodeId 节点 ID
     * @return 通道（可能为 null）
     */
    public Channel get(Long nodeId) {
        return channels.get(nodeId);
    }

    /**
     * 节点是否在线。
     *
     * @param nodeId 节点 ID
     * @return 在线返回 true
     */
    public boolean isOnline(Long nodeId) {
        Channel c = channels.get(nodeId);
        return c != null && c.isActive();
    }

    /**
     * 向节点发送一帧。
     *
     * @param nodeId  节点 ID
     * @param type    消息类型
     * @param payload 消息对象
     * @return 发送成功（节点在线）返回 true
     */
    public boolean send(Long nodeId, MessageType type, Object payload) {
        Channel c = channels.get(nodeId);
        if (c == null || !c.isActive()) {
            return false;
        }
        c.writeAndFlush(ProtocolCodec.encode(type, payload));
        return true;
    }

    /**
     * @return 在线节点 ID 集合
     */
    public Set<Long> onlineNodeIds() {
        return channels.entrySet().stream()
                .filter(e -> e.getValue().isActive())
                .map(e -> e.getKey())
                .collect(Collectors.toSet());
    }
}
