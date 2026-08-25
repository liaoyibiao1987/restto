package com.restto.manager.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * 将 LengthFieldBasedFrameDecoder 切分出的完整帧解码为 {@link InboundMessage}。
 */
public class FrameDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        out.add(ProtocolCodec.decode(msg));
    }
}
