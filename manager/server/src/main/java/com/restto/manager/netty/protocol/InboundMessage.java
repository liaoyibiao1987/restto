package com.restto.manager.netty.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 解码后的入站消息：消息类型 + JSON payload。
 */
public final class InboundMessage {

    /** 消息类型。 */
    public final MessageType type;

    /** JSON payload。 */
    public final JsonNode payload;

    /**
     * @param type    消息类型
     * @param payload JSON payload
     */
    public InboundMessage(MessageType type, JsonNode payload) {
        this.type = type;
        this.payload = payload;
    }
}
