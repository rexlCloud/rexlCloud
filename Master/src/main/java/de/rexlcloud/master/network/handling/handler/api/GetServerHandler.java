package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.Wrapper;
import de.rexlcloud.core.api.exceptions.ServerNotFoundException;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

public class GetServerHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_GET_SERVER;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        serverManager.getServerByName(packet.getData()[0]).ifPresentOrElse(requestServer -> {
            Wrapper wrapper = requestServer.getWrapper() == null ? null : requestServer.getWrapper().getApiWrapper();
            de.rexlcloud.core.api.Server serverResponse = new de.rexlcloud.core.api.Server(requestServer, wrapper);
            response(packet, serverResponse);
        }, () -> response(packet, new ServerNotFoundException(packet.getData()[0])));
    }
}
