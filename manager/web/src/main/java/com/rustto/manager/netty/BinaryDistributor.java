package com.rustto.manager.netty;

import com.rustto.manager.netty.message.ProtocolMessages.BinaryPush;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * 二进制分发器：读取文件 → base64 分片 → 经 Netty 下发 BINARY_PUSH。
 *
 * <p>说明：基础实现整文件读入内存后分片；大文件生产场景应改为流式分片。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BinaryDistributor {

    /** 每个分片的 base64 字符数（约 384 KiB 原始字节）。 */
    private static final int CHUNK_CHARS = 512 * 1024;

    private final ConnectionRegistry connectionRegistry;

    /**
     * 将二进制文件分片下发到指定节点。
     *
     * @param nodeId   节点 ID
     * @param file     二进制文件
     * @param version  版本号
     * @param checksum 整文件 sha256
     * @return 成功下发返回 true（节点离线或读取失败返回 false）
     */
    public boolean push(Long nodeId, Path file, String version, String checksum) {
        Channel channel = connectionRegistry.get(nodeId);
        if (channel == null || !channel.isActive()) {
            log.warn("binary push: node {} offline", nodeId);
            return false;
        }
        try {
            byte[] all = Files.readAllBytes(file);
            String base64 = Base64.getEncoder().encodeToString(all);
            int total = (base64.length() + CHUNK_CHARS - 1) / CHUNK_CHARS;
            for (int i = 0; i < total; i++) {
                int from = i * CHUNK_CHARS;
                int to = Math.min(base64.length(), from + CHUNK_CHARS);
                BinaryPush push = new BinaryPush(version, checksum, i, total, base64.substring(from, to));
                channel.writeAndFlush(ProtocolCodec.encode(MessageType.BINARY_PUSH, push));
            }
            log.info("binary v{} pushed to node {} ({} chunks)", version, nodeId, total);
            return true;
        } catch (Exception e) {
            log.error("binary push failed: {}", e.getMessage(), e);
            return false;
        }
    }
}
