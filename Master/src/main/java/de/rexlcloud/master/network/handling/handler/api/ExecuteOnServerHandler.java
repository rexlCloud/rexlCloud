package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.ServerNotFoundException;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerExecuteCommandPacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

public class ExecuteOnServerHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_EXECUTE_ON_SERVER;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
        RexlCloudMaster.getInstance().getServerManager().getServerByName(packet.getData()[0]).ifPresentOrElse(executeServer -> {
            ServerExecuteCommandPacket serverExecutePacket = new ServerExecuteCommandPacket(executeServer.getName(), packet.getData()[1]);
            PacketContainer packetContainer = new PacketContainer(PacketType.SERVER_EXECUTE_COMMAND, serverExecutePacket);
            executeServer.getWrapper().getChannel().writeAndFlush(packetContainer);
            response(packet, true);
        }, () -> response(packet, new ServerNotFoundException(packet.getData()[0])));
    }
}
