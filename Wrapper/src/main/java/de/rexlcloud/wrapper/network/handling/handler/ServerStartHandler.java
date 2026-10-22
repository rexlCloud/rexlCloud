package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerStartPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import de.rexlcloud.wrapper.server.Server;
import io.netty.channel.ChannelHandlerContext;

public class ServerStartHandler implements IPacketHandler<ServerStartPacket> {
    @Override
    public PacketType getType() {
        return PacketType.SERVER_START;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ServerStartPacket packet) {
        var serverManager = RexlCloudWrapper.getInstance().getServerManager();
        var server = new Server(packet.getName(), packet.getServerGroup(), packet.getPort(), packet.getKey(), packet.isMaintenance(),
                packet.getForwarding());
        serverManager.startServer(server);
    }

}
