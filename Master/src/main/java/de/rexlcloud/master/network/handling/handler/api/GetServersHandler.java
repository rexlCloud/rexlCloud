package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.Wrapper;
import de.rexlcloud.core.api.exceptions.ServerGroupNotFoundException;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

public class GetServersHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_GET_SERVERS;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        if (packet.getData() == null || packet.getData().length == 0) {
            de.rexlcloud.core.api.Server[] servers = serverManager.getServers().stream().map(serverMap -> {
                Wrapper wrapper = serverMap.getWrapper() == null ? null : serverMap.getWrapper().getApiWrapper();
                return new de.rexlcloud.core.api.Server(serverMap, wrapper);
            }).toArray(de.rexlcloud.core.api.Server[]::new);
            response(packet, servers);
        } else {
            RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(packet.getData()[0]).ifPresentOrElse(serverGroup -> {
                de.rexlcloud.core.api.Server[] servers = serverManager.getServersFromGroup(serverGroup).stream().map(serverMap -> {
                    Wrapper wrapper = serverMap.getWrapper() == null ? null : serverMap.getWrapper().getApiWrapper();
                    return new de.rexlcloud.core.api.Server(serverMap, wrapper);
                }).toArray(de.rexlcloud.core.api.Server[]::new);
                response(packet, servers);
            }, () -> response(packet, new ServerGroupNotFoundException(packet.getData()[0])));
        }
    }
}
