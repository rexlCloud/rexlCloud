package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.master.network.handling.WrapperPacketHandler;
import de.rexlcloud.master.wrapper.Wrapper;
import io.netty.channel.ChannelHandlerContext;

public class WrapperStopHandler implements WrapperPacketHandler<Packet> {
    @Override
    public PacketType getType() {
        return PacketType.WRAPPER_STOP;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, Packet packet) {

    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, Wrapper wrapper, Packet packet) {
        wrapper.setInShutdown(true);
    }
}
