package com.restto.manager.netty;

/**
 * Netty 协议消息类型（字节码与 Rust client 的 protocol::MessageType 一一对应）。
 */
public enum MessageType {

    /** 客户端注册（C→S）。 */
    REGISTER((byte) 1),
    /** 注册应答（S→C）。 */
    REGISTER_ACK((byte) 2),
    /** 心跳（双向）。 */
    HEARTBEAT((byte) 3),
    /** 任务下发（S→C）。 */
    TASK_COMMAND((byte) 4),
    /** 任务结果回传（C→S）。 */
    TASK_RESULT((byte) 5),
    /** 二进制下发分片（S→C）。 */
    BINARY_PUSH((byte) 6),
    /** 二进制下发应答（C→S）。 */
    BINARY_ACK((byte) 7);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    /**
     * @return 字节码
     */
    public byte code() {
        return code;
    }

    /**
     * 由字节码还原消息类型。
     *
     * @param code 字节码
     * @return 消息类型
     * @throws IllegalArgumentException 未知字节码
     */
    public static MessageType fromCode(byte code) {
        for (MessageType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown message type code: " + code);
    }
}
