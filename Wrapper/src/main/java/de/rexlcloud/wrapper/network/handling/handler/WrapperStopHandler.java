package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.wrapper.commands.Commands;
import io.netty.channel.ChannelHandlerContext;

public class WrapperStopHandler implements IPacketHandler<Packet> {
    @Override
    public PacketType getType() {
        return PacketType.WRAPPER_STOP;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, Packet packet) {
        Commands.stop();
    }

}
