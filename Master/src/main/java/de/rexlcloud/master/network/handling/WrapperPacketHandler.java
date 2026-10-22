package de.rexlcloud.master.network.handling;

import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.master.wrapper.Wrapper;
import io.netty.channel.ChannelHandlerContext;

public interface WrapperPacketHandler<T extends Packet> extends IPacketHandler<T> {

    default void handle(ChannelHandlerContext ctx, Wrapper wrapper, Object o) {
        this.handlePacket(ctx, wrapper, o == null ? null : (T) o);
    }

    void handlePacket(ChannelHandlerContext ctx, Wrapper wrapper, T packet);

}
