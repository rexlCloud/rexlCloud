package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.ServerLocation;
import de.rexlcloud.core.api.exceptions.PlayerNotFoundException;
import de.rexlcloud.core.api.exceptions.ServerNotFoundException;
import de.rexlcloud.core.api.exceptions.WrapperNotFoundException;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerChangeServerPacket;
import de.rexlcloud.core.network.packets.PlayerTeleportPacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.UUID;

public class PlayerChangeServerTeleportHandler implements ApiPacketHandler<Object> {
    @Override
    public PacketType getType() {
        return PacketType.API_PLAYER_CHANGE_SERVER_AND_TELEPORT;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<Object> packet) {
        var playerUUID = UUID.fromString((String) packet.getData()[0]);
        var targetServerName = ((String) packet.getData()[1]);
        var location = (ServerLocation) packet.getData()[2];
        RexlCloudMaster.getInstance().getPlayerManager().getPlayer(playerUUID).ifPresentOrElse(player -> {
            RexlCloudMaster.getInstance().getServerManager().getServerByName(targetServerName).ifPresentOrElse(targetServer -> {
                var serverWrapper = targetServer.getWrapper();
                if (serverWrapper == null) {
                    response(packet, new WrapperNotFoundException("from server " + player.getServer().getName()));
                    return;
                }
                RexlCloudMaster.getInstance().getWrapperManager().getWrapperFromApiWrapper(player.getProxyServer().getWrapper()).ifPresentOrElse(proxyWrapper -> {
                    var containerTeleport = new PacketContainer(PacketType.PLAYER_TELEPORT,
                            new PlayerTeleportPacket(playerUUID, targetServer.getName(), location, 5000));
                    serverWrapper.getChannel().writeAndFlush(containerTeleport);

                    var containerChangeServer = new PacketContainer(PacketType.PLAYER_CHANGE_SERVER,
                            new PlayerChangeServerPacket(playerUUID, targetServer.getName()));
                    proxyWrapper.getChannel().writeAndFlush(containerChangeServer);
                    response(packet, true);
                }, () -> response(packet, new WrapperNotFoundException("from server " + player.getProxyServer().getName())));
            }, () -> response(packet, new ServerNotFoundException(targetServerName)));
        }, () -> response(packet, new PlayerNotFoundException(playerUUID)));
    }
}
