package de.rexlcloud.core.network.codec;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageCodec;

import javax.net.ssl.SSLException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One frame (see {@link PacketPipeline}) is one {@link PacketContainer} as UTF-8 JSON.
 */
public class PacketCodec extends MessageToMessageCodec<ByteBuf, PacketContainer> {

    @Override
    protected void encode(ChannelHandlerContext ctx, PacketContainer container, List<Object> out) {
        out.add(ByteBufUtil.writeUtf8(ctx.alloc(), PacketJson.write(container)));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf frame, List<Object> out) {
        var text = frame.toString(StandardCharsets.UTF_8);
        try {
            out.add(PacketJson.read(text));
        } catch (RuntimeException e) {
            // A message that cannot be read is dropped; the connection stays usable
            Console.error("Invalid message from %s dropped: %s", ctx.channel().remoteAddress(), e.getMessage());
        }
    }

    /**
     * Frames that are too large or not decodable mean the other side does not speak this protocol (e.g. an old
     * version): one line instead of a stack trace, and the connection is closed.
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof DecoderException && cause.getCause() instanceof SSLException tls) {
            Console.error("Encrypted connection with %s failed: %s", ctx.channel().remoteAddress(),
                    tls.getCause() != null ? tls.getCause().getMessage() : tls.getMessage());
            ctx.close();
            return;
        }
        if (cause instanceof DecoderException) {
            Console.error("Connection from %s closed, it does not speak the cloud protocol: %s",
                    ctx.channel().remoteAddress(), cause.getMessage());
            ctx.close();
            return;
        }
        ctx.fireExceptionCaught(cause);
    }

}
