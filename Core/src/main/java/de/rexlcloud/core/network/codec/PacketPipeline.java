package de.rexlcloud.core.network.codec;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

/**
 * The protocol of every connection between master, wrappers and servers: frames of a 4-byte length and a JSON
 * {@link de.rexlcloud.core.network.PacketContainer}.
 */
public final class PacketPipeline {

    /**
     * Largest frame accepted. Template files are sent in 1 MB chunks, which are about 1.4 MB as JSON.
     */
    public static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;

    private PacketPipeline() {
    }

    /**
     * Adds the codec in front of the given handler.
     */
    public static void configure(ChannelPipeline pipeline) {
        pipeline.addLast("frame-decoder", new LengthFieldBasedFrameDecoder(MAX_FRAME_BYTES, 0, 4, 0, 4));
        pipeline.addLast("frame-encoder", new LengthFieldPrepender(4));
        pipeline.addLast("packet-codec", new PacketCodec());
    }

}
