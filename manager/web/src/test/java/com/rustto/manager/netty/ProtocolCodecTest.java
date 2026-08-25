package com.rustto.manager.netty;

import com.rustto.manager.netty.message.ProtocolMessages.RegisterMessage;
import com.rustto.manager.netty.message.ProtocolMessages.TaskResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 协议编解码测试（与 Rust 端字节级对齐）。
 */
class ProtocolCodecTest {

    @Test
    void roundtripRegister() {
        RegisterMessage msg = new RegisterMessage("n1", "tok", "0.1.0");
        byte[] bytes = ProtocolCodec.encodeBytes(MessageType.REGISTER, msg);
        // 校验魔数与帧长度字段
        byte[] expectedMagic = {0x52, 0x55, 0x53, 0x54};
        assertArrayEquals(expectedMagic, new byte[]{bytes[0], bytes[1], bytes[2], bytes[3]});
        int length = ((bytes[4] & 0xFF) << 24) | ((bytes[5] & 0xFF) << 16)
                | ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        assertEquals(MessageType.REGISTER.code(), bytes[8]);
        assertEquals(bytes.length - 8, length);

        InboundMessage decoded = ProtocolCodec.decode(Unpooled.wrappedBuffer(bytes));
        assertEquals(MessageType.REGISTER, decoded.type);
        // snake_case 字段名
        assertEquals("n1", decoded.payload.get("node_name").asText());
        assertEquals("tok", decoded.payload.get("node_token").asText());
    }

    @Test
    void roundtripTaskResultSnakeCase() throws Exception {
        TaskResult result = new TaskResult(7L, "success", "/a/b.tar.gz", 123L, "deadbeef", null);
        byte[] bytes = ProtocolCodec.encodeBytes(MessageType.TASK_RESULT, result);
        InboundMessage decoded = ProtocolCodec.decode(Unpooled.wrappedBuffer(bytes));
        TaskResult back = ProtocolCodec.MAPPER.treeToValue(decoded.payload, TaskResult.class);
        assertEquals(7L, back.getTaskId());
        assertEquals("success", back.getStatus());
        assertEquals(Long.valueOf(123L), back.getSize());
        assertEquals("deadbeef", back.getChecksum());
    }

    @Test
    void rejectsInvalidMagic() {
        ByteBuf bad = Unpooled.buffer();
        bad.writeBytes(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x01});
        assertThrows(IllegalArgumentException.class, () -> ProtocolCodec.decode(bad));
    }

    @Test
    void rejectsUnknownType() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(ProtocolCodec.MAGIC);
        buf.writeInt(1);
        buf.writeByte((byte) 99); // 未知类型
        assertThrows(IllegalStateException.class, () -> ProtocolCodec.decode(buf));
    }
}
