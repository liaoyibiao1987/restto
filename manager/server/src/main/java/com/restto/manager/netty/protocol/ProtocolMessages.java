package com.restto.manager.netty.message;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Netty 协议各消息 DTO（字段名经 SNAKE_CASE 映射，与 Rust 端 snake_case 对齐）。
 */
public final class ProtocolMessages {

    private ProtocolMessages() {}

    /** 节点注册消息（C→S）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterMessage {
        /** 节点名称。 */
        private String nodeName;
        /** 节点 Token。 */
        private String nodeToken;
        /** 客户端版本。 */
        private String version;
    }

    /** 注册应答（S→C）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterAck {
        /** 是否成功。 */
        private boolean success;
        /** 提示信息。 */
        private String message;
        /** 节点 ID（成功时）。 */
        private Long nodeId;
    }

    /** 心跳。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Heartbeat {
        /** Unix 秒。 */
        private long timestamp;
    }

    /** 任务下发指令（S→C）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskCommand {
        /** 任务 ID。 */
        private long taskId;
        /** 模块名。 */
        private String module;
        /** 任务参数（任意 JSON）。 */
        private JsonNode args;
    }

    /** 任务执行结果（C→S）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskResult {
        /** 任务 ID。 */
        private long taskId;
        /** 状态：success / failed。 */
        private String status;
        /** 产物路径。 */
        private String filePath;
        /** 产物大小。 */
        private Long size;
        /** 产物 sha256。 */
        private String checksum;
        /** 错误信息。 */
        private String error;
    }

    /** 二进制下发分片（S→C），data 为 base64。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BinaryPush {
        /** 版本。 */
        private String version;
        /** 整文件 sha256。 */
        private String checksum;
        /** 当前分片序号。 */
        private int chunkIndex;
        /** 总分片数。 */
        private int totalChunks;
        /** base64 分片数据。 */
        private String data;
    }

    /** 二进制下发应答（C→S）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BinaryAck {
        /** 版本。 */
        private String version;
        /** 是否成功。 */
        private boolean success;
        /** 错误信息。 */
        private String error;
    }
}
