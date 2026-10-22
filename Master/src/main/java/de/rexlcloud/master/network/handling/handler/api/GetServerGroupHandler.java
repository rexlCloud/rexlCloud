package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.ServerGroupNotFoundException;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

public class GetServerGroupHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_SERVER_GROUP;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
        ServerGroup serverGroup = RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(packet.getData()[0]).orElse(null);
        if (serverGroup == null) {
            response(packet, new ServerGroupNotFoundException(packet.getData()[0]));
            return;
        }
        response(packet, serverGroup);
    }
}
