package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.ServerNotFoundException;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

public class StopServerHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_STOP_SERVER;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        RexlCloudMaster.getInstance().getServerManager().getServerByName(packet.getData()[0]).ifPresentOrElse(serverToStop -> {
            serverManager.stopServer(serverToStop);
            response(packet, true);
        }, () -> response(packet, new ServerNotFoundException(packet.getData()[0])));
    }
}
