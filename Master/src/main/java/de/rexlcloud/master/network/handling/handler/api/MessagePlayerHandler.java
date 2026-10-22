package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.PlayerNotFoundException;
import de.rexlcloud.core.api.exceptions.WrapperNotFoundException;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerMessagePacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.UUID;

public class MessagePlayerHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_SEND_MESSAGE_TO_PLAYER;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
        var playerUUID = UUID.fromString(packet.getData()[0]);
        var message = packet.getData()[1];
        RexlCloudMaster.getInstance().getPlayerManager().getPlayer(playerUUID).ifPresentOrElse(player -> {
            RexlCloudMaster.getInstance().getWrapperManager().getWrapperFromApiWrapper(player.getProxyServer().getWrapper()).ifPresentOrElse(wrapper -> {
                var container = new PacketContainer(PacketType.PLAYER_SEND_MESSAGE,
                        new PlayerMessagePacket(playerUUID, player.getProxyServer().getName(), message));
                wrapper.getChannel().writeAndFlush(container);
                response(packet, true);
            }, () -> response(packet, new WrapperNotFoundException("from server " + player.getProxyServer().getName())));
        }, () -> response(packet, new PlayerNotFoundException(playerUUID)));
    }
}
