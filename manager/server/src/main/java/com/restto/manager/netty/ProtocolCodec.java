package com.restto.manager.netty;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;

/**
 * Netty 协议编解码（与 Rust client 的 core::protocol 字节级对齐）。
 *
 * <pre>
 * | magic 4B "RUST"(0x52555354) | length 4B BE(=1+payloadLen) | type 1B | payload JSON |
 * </pre>
 * <p>使用独立的 SNAKE_CASE ObjectMapper，不影响 REST 层的驼峰 JSON。
 */
public final class ProtocolCodec {

    /** 魔数 "RUST"。 */
    public static final byte[] MAGIC = {0x52, 0x55, 0x53, 0x54};

    /** 单帧最大字节数（256 MiB）。 */
    public static final int MAX_FRAME = 256 * 1024 * 1024;

    /** 头部固定长度：magic(4) + length(4) + type(1)。 */
    public static final int HEADER_LEN = 9;

    /** SNAKE_CASE JSON 映射器。 */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ProtocolCodec() {}

    /**
     * 编码为字节数组。
     *
     * @param type    消息类型
     * @param payload 消息对象
     * @return 完整帧字节
     */
    public static byte[] encodeBytes(MessageType type, Object payload) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(payload);
            ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN + json.length);
            buf.put(MAGIC);
            buf.putInt(1 + json.length);
            buf.put(type.code());
            buf.put(json);
            return buf.array();
        } catch (Exception e) {
            throw new IllegalStateException("encode frame failed", e);
        }
    }

    /**
     * 编码为 Netty ByteBuf（堆缓冲）。
     *
     * @param type    消息类型
     * @param payload 消息对象
     * @return ByteBuf
     */
    public static ByteBuf encode(MessageType type, Object payload) {
        return Unpooled.wrappedBuffer(encodeBytes(type, payload));
    }

    /**
     * 解码一个完整帧（需已由帧解码器切分）。
     *
     * @param frame 完整帧 ByteBuf
     * @return 入站消息
     */
    public static InboundMessage decode(ByteBuf frame) {
        if (frame.readableBytes() < HEADER_LEN) {
            throw new IllegalArgumentException("frame too short: " + frame.readableBytes());
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (frame.readByte() != MAGIC[i]) {
                throw new IllegalArgumentException("invalid magic");
            }
        }
        int length = frame.readInt();
        byte typeCode = frame.readByte();
        int payloadLen = length - 1;
        byte[] payloadBytes = new byte[payloadLen];
        frame.readBytes(payloadBytes);
        try {
            MessageType type = MessageType.fromCode(typeCode);
            JsonNode node = MAPPER.readTree(payloadBytes);
            return new InboundMessage(type, node);
        } catch (Exception e) {
            throw new IllegalStateException("decode frame failed", e);
        }
    }
}
