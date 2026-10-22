package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import de.rexlcloud.wrapper.server.Server;
import io.netty.channel.ChannelHandlerContext;

public class ServerKillHandler implements IPacketHandler<ServerStopPacket> {
    @Override
    public PacketType getType() {
        return PacketType.SERVER_KILL;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ServerStopPacket packet) {
        var serverManager = RexlCloudWrapper.getInstance().getServerManager();
        serverManager.getServerByNameAndKey(packet.getName(), packet.getKey()).ifPresent(Server::kill);
    }

}
