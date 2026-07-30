package com.rustto.manager.netty;

import com.rustto.manager.entity.BackupNode;
import com.rustto.manager.netty.message.ProtocolMessages.BinaryAck;
import com.rustto.manager.netty.message.ProtocolMessages.Heartbeat;
import com.rustto.manager.netty.message.ProtocolMessages.RegisterAck;
import com.rustto.manager.netty.message.ProtocolMessages.RegisterMessage;
import com.rustto.manager.netty.message.ProtocolMessages.TaskResult;
import com.rustto.manager.service.BackupRecordService;
import com.rustto.manager.service.NodeService;
import com.rustto.manager.service.WorkflowExecutionService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 服务端业务处理器：处理节点注册、心跳、任务结果、二进制应答。
 *
 * <p>每个连接一个实例，由 {@link NettyServer} 的 ChannelInitializer 构造时注入 Service。
 */
@Slf4j
@RequiredArgsConstructor
public class ServerChannelHandler extends SimpleChannelInboundHandler<InboundMessage> {

    private final NodeService nodeService;

    private final BackupRecordService backupRecordService;

    private final ConnectionRegistry connectionRegistry;

    // ⚠️ 需人类审核（AGENT.MD §5.2）：在核心消息处理热路径注入工作流引擎。
    private final WorkflowExecutionService workflowExecutionService;

    /** 当前连接绑定的节点 ID（注册成功后赋值）。 */
    private Long boundNodeId;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, InboundMessage msg) {
        switch (msg.type) {
            case REGISTER:
                handleRegister(ctx, msg);
                break;
            case HEARTBEAT:
                handleHeartbeat();
                ctx.writeAndFlush(ProtocolCodec.encode(MessageType.HEARTBEAT,
                        new Heartbeat(System.currentTimeMillis() / 1000L)));
                break;
            case TASK_RESULT:
                handleTaskResult(msg);
                break;
            case BINARY_ACK:
                handleBinaryAck(msg);
                break;
            default:
                log.warn("unexpected inbound message: {}", msg.type);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (boundNodeId != null) {
            connectionRegistry.remove(boundNodeId);
            nodeService.markOffline(boundNodeId);
            // ⚠️ 需人类审核：节点断连，把该节点在途的工作流任务标记失败，防止永久挂起。
            workflowExecutionService.onNodeDisconnected(boundNodeId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("channel error from node {}: {}", boundNodeId, cause.getMessage(), cause);
        ctx.close();
    }

    /**
     * 处理注册：校验节点名 + Token，注册连接并回 ACK。
     *
     * @param ctx 上下文
     * @param msg 入站消息
     */
    private void handleRegister(ChannelHandlerContext ctx, InboundMessage msg) {
        try {
            RegisterMessage reg = ProtocolCodec.MAPPER.treeToValue(msg.payload, RegisterMessage.class);
            BackupNode node = nodeService.findByNodeName(reg.getNodeName());
            RegisterAck ack;
            if (node != null && node.getNodeToken() != null
                    && node.getNodeToken().equals(reg.getNodeToken())) {
                this.boundNodeId = node.getId();
                node.setVersion(reg.getVersion());
                node.setStatus("online");
                nodeService.updateById(node);
                connectionRegistry.register(node.getId(), ctx.channel());
                ack = new RegisterAck(true, "registered", node.getId());
                log.info("node {} registered (version={})", node.getId(), reg.getVersion());
            } else {
                ack = new RegisterAck(false, "invalid node name or token", null);
                log.warn("register rejected: name={}", reg.getNodeName());
            }
            ctx.writeAndFlush(ProtocolCodec.encode(MessageType.REGISTER_ACK, ack));
            if (!ack.isSuccess()) {
                ctx.close();
            }
        } catch (Exception e) {
            log.error("handle register failed: {}", e.getMessage(), e);
            ctx.close();
        }
    }

    /**
     * 处理心跳：刷新节点在线时间。
     */
    private void handleHeartbeat() {
        if (boundNodeId != null) {
            nodeService.updateHeartbeat(boundNodeId);
        }
    }

    /**
     * 处理任务结果：落库。
     *
     * @param msg 入站消息
     */
    private void handleTaskResult(InboundMessage msg) {
        try {
            TaskResult result = ProtocolCodec.MAPPER.treeToValue(msg.payload, TaskResult.class);
            backupRecordService.applyResult(result.getTaskId(), boundNodeId, result);
            // ⚠️ 需人类审核：把任务结果回调给工作流引擎（无工作流等待时为廉价 no-op）。
            workflowExecutionService.onTaskResult(result.getTaskId(), result.getStatus(), result.getError());
            log.info("task #{} result: {}", result.getTaskId(), result.getStatus());
        } catch (Exception e) {
            log.error("handle task result failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理二进制应答（日志即可）。
     *
     * @param msg 入站消息
     */
    private void handleBinaryAck(InboundMessage msg) {
        try {
            BinaryAck ack = ProtocolCodec.MAPPER.treeToValue(msg.payload, BinaryAck.class);
            log.info("binary ack v{} success={}", ack.getVersion(), ack.isSuccess());
        } catch (Exception e) {
            log.warn("handle binary ack failed: {}", e.getMessage());
        }
    }
}
