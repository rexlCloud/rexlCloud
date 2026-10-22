package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.ServerGroupNotFoundException;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import de.rexlcloud.master.server.Server;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;

public class StartServerHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_START_SERVER;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(packet.getData()[0]).ifPresentOrElse(serverGroup -> {
            var amount = Integer.parseInt(packet.getData()[1]);
            var startedServer = new ArrayList<Server>();
            for (int i = 0; i < amount; i++) {
                Server newServer = new Server(serverManager.getServerNameForNextServer(serverGroup), serverGroup);
                if (serverManager.startServer(newServer)) {
                    startedServer.add(newServer);
                }
            }
            var servers = startedServer.stream().map(serverMap ->
                            new de.rexlcloud.core.api.Server(serverMap, serverMap.getWrapper().getApiWrapper()))
                    .toArray(de.rexlcloud.core.api.Server[]::new);
            response(packet, servers);
        }, () -> response(packet, new ServerGroupNotFoundException(packet.getData()[0])));
    }
}
