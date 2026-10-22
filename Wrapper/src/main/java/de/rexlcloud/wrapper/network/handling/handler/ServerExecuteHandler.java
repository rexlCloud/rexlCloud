package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerExecuteCommandPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import io.netty.channel.ChannelHandlerContext;

public class ServerExecuteHandler implements IPacketHandler<ServerExecuteCommandPacket> {
    @Override
    public PacketType getType() {
        return PacketType.SERVER_EXECUTE_COMMAND;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ServerExecuteCommandPacket packet) {
        var serverManager = RexlCloudWrapper.getInstance().getServerManager();
        serverManager.getServerByName(packet.getServerName()).ifPresent(server -> server.execute(packet.getCommand()));
    }

}
