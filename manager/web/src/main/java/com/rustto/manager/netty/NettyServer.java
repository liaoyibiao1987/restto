package com.rustto.manager.netty;

import com.rustto.manager.config.NettyProperties;
import com.rustto.manager.service.BackupRecordService;
import com.rustto.manager.service.NodeService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Netty TCP 服务端：接收 Rust 客户端长连接。
 *
 * <p>在独立线程中启动，避免阻塞 Spring 启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettyServer {

    private final NettyProperties nettyProperties;

    private final ConnectionRegistry connectionRegistry;

    private final NodeService nodeService;

    private final BackupRecordService backupRecordService;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private Channel serverChannel;

    /**
     * 启动 Netty（后台线程）。
     */
    @PostConstruct
    public void start() {
        Thread thread = new Thread(this::run, "rustto-netty-server");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 运行 Netty（阻塞，直到关闭）。
     */
    private void run() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new LengthFieldBasedFrameDecoder(
                                            ProtocolCodec.MAX_FRAME, 4, 4, 0, 0))
                                    .addLast(new FrameDecoder())
                                    .addLast(new ServerChannelHandler(
                                            nodeService, backupRecordService, connectionRegistry));
                        }
                    });
            serverChannel = bootstrap.bind(nettyProperties.getPort()).sync().channel();
            log.info("Netty server bound on port {}", nettyProperties.getPort());
            serverChannel.closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Netty server interrupted");
        } catch (Exception e) {
            log.error("Netty server failed: {}", e.getMessage(), e);
        } finally {
            shutdownGroups();
        }
    }

    /**
     * 关闭 Netty。
     */
    @PreDestroy
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        shutdownGroups();
        log.info("Netty server stopped");
    }

    private void shutdownGroups() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
