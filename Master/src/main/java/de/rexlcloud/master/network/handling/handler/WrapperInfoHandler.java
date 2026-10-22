package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.WrapperInfoPacket;
import de.rexlcloud.master.network.handling.WrapperPacketHandler;
import de.rexlcloud.master.wrapper.Wrapper;
import io.netty.channel.ChannelHandlerContext;

public class WrapperInfoHandler implements WrapperPacketHandler<WrapperInfoPacket> {

    @Override
    public PacketType getType() {
        return PacketType.WRAPPER_INFO;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, WrapperInfoPacket packet) {
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, Wrapper wrapper, WrapperInfoPacket packet) {
        wrapper.handleWrapperInfoPacket(packet);
    }
}
