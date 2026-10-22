package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

public class GetServerGroupsHandler implements ApiPacketHandler<Object> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_SERVER_GROUPS;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<Object> packet) {
        ServerGroup[] serverGroups = RexlCloudMaster.getInstance().getServerGroupManager().getServerGroups().toArray(ServerGroup[]::new);
        response(packet, serverGroups);
    }
}
