package com.restto.manager.netty.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 消息类型枚举测试（确保与 Rust 端字节码一致）。
 */
class MessageTypeTest {

    @Test
    void codeValuesMatchRust() {
        assertEquals(1, MessageType.REGISTER.code());
        assertEquals(2, MessageType.REGISTER_ACK.code());
        assertEquals(3, MessageType.HEARTBEAT.code());
        assertEquals(4, MessageType.TASK_COMMAND.code());
        assertEquals(5, MessageType.TASK_RESULT.code());
        assertEquals(6, MessageType.BINARY_PUSH.code());
        assertEquals(7, MessageType.BINARY_ACK.code());
    }

    @Test
    void fromCodeRoundtrip() {
        for (MessageType t : MessageType.values()) {
            assertEquals(t, MessageType.fromCode(t.code()));
        }
    }

    @Test
    void fromCodeRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> MessageType.fromCode((byte) 99));
    }
}
