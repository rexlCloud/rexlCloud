package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerConsolePacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.master.RexlCloudMaster;
import io.netty.channel.ChannelHandlerContext;

public class ServerConsoleHandler implements IPacketHandler<ServerConsolePacket> {
    @Override
    public PacketType getType() {
        return PacketType.SERVER_CONSOLE_LOG;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ServerConsolePacket packet) {
        if (packet.getMessage() == null) {
            return;
        }
        RexlCloudMaster.getInstance().getServerManager().getServerByName(packet.getServerName())
                .ifPresent(server -> server.getLogList().add(packet.getMessage()));
    }


}
