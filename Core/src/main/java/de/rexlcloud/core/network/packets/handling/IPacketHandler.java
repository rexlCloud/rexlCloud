package de.rexlcloud.core.network.packets.handling;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.types.Packet;
import io.netty.channel.ChannelHandlerContext;

public interface IPacketHandler<T extends Packet> {

    default void handle(ChannelHandlerContext ctx, Object o) {
        handlePacket(ctx, o == null ? null : (T) o);
    }

    default boolean requiresRegisteredWrapper() {
        return true;
    }

    PacketType getType();

    void handlePacket(ChannelHandlerContext ctx, T packet);

}
