package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.ServerLocation;
import de.rexlcloud.core.api.exceptions.PlayerNotFoundException;
import de.rexlcloud.core.api.exceptions.WrapperNotFoundException;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerTeleportPacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.UUID;

public class PlayerTeleportHandler implements ApiPacketHandler<Object> {
    @Override
    public PacketType getType() {
        return PacketType.API_PLAYER_TELEPORT;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<Object> packet) {
        var playerUUID = UUID.fromString((String) packet.getData()[0]);
        var location = (ServerLocation) packet.getData()[1];
        RexlCloudMaster.getInstance().getPlayerManager().getPlayer(playerUUID).ifPresentOrElse(player -> {
            RexlCloudMaster.getInstance().getWrapperManager().getWrapperFromApiWrapper(player.getServer().getWrapper()).ifPresentOrElse(wrapper -> {
                var container = new PacketContainer(PacketType.PLAYER_TELEPORT,
                        new PlayerTeleportPacket(playerUUID, player.getServer().getName(), location, 0));
                wrapper.getChannel().writeAndFlush(container);
                response(packet, true);
            }, () -> response(packet, new WrapperNotFoundException("from server " + player.getServer().getName())));
        }, () -> response(packet, new PlayerNotFoundException(playerUUID)));
    }
}
