package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import de.rexlcloud.wrapper.server.Server;
import io.netty.channel.ChannelHandlerContext;

public class ServerStopHandler implements IPacketHandler<ServerStopPacket> {
    @Override
    public PacketType getType() {
        return PacketType.SERVER_STOP;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ServerStopPacket packet) {
        var serverManager = RexlCloudWrapper.getInstance().getServerManager();
        // Exactly this run: a stop for an earlier run of the same server is ignored
        serverManager.getServerByNameAndKey(packet.getName(), packet.getKey()).ifPresent(Server::stop);
    }

}
