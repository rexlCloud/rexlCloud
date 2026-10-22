package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.PlayerNotFoundException;
import de.rexlcloud.core.api.exceptions.ServerNotFoundException;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacketToSpecific;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.UUID;

public class GetPlayerLocationHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_GET_PLAYER_LOCATION;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
            var uuid = UUID.fromString(packet.getData()[0]);
            RexlCloudMaster.getInstance().getPlayerManager().getPlayer(uuid).ifPresentOrElse(player -> {
                var specApiRequestPacket = new ApiRequestPacketToSpecific(packet, player.getServer().getName());
                RexlCloudMaster.getInstance().getServerManager().getServerByApiServer(player.getServer()).ifPresentOrElse(targetServer -> {
                    targetServer.sendPacket(new PacketContainer(PacketType.API_REQUEST_GET_PLAYER_LOCATION, specApiRequestPacket));
                }, () -> response(packet, new ServerNotFoundException(player.getServer())));
            }, () -> response(packet, new PlayerNotFoundException(uuid)));
    }
}
