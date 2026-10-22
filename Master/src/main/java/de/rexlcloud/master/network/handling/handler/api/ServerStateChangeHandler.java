package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.ServerNotFoundException;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

public class ServerStateChangeHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_SERVER_STATE_CHANGE;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
        RexlCloudMaster.getInstance().getServerManager().getServerByName(packet.getServer()).ifPresentOrElse(server -> {
            server.setState(packet.getData()[0]);
            server.sendApiUpdate();
            response(packet, true);
        }, () -> response(packet, new ServerNotFoundException(packet.getServer())));
    }
}
