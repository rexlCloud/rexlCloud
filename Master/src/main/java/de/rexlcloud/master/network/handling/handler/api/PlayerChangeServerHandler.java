package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.PlayerNotFoundException;
import de.rexlcloud.core.api.exceptions.ServerNotFoundException;
import de.rexlcloud.core.api.exceptions.WrapperNotFoundException;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerChangeServerPacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class PlayerChangeServerHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_PLAYER_CHANGE_SERVER;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
        var playerUUID = UUID.fromString(packet.getData()[0]);
        var serverName = new AtomicReference<>("");
        if (packet.getData()[1] == null) {
            RexlCloudMaster.getInstance().getServerManager()
                    .getServersByType(ServerType.SERVER).stream()
                    .filter(server1 -> server1.getGroup().isFallback())
                    .min(Comparator.comparingInt(value -> value.getOnlinePlayers().size()))
                    .ifPresent(server1 -> serverName.set(server1.getName()));
        } else {
            serverName.set(packet.getData()[1]);
        }
        RexlCloudMaster.getInstance().getPlayerManager().getPlayer(playerUUID).ifPresentOrElse(player -> {
            if (!RexlCloudMaster.getInstance().getServerManager().existServerWithName(serverName.get())) {
                response(packet, new ServerNotFoundException(serverName.get()));
                return;
            }
            RexlCloudMaster.getInstance().getWrapperManager().getWrapperFromApiWrapper(player.getProxyServer().getWrapper()).ifPresentOrElse(wrapper -> {
                var container = new PacketContainer(PacketType.PLAYER_CHANGE_SERVER, new PlayerChangeServerPacket(playerUUID, serverName.get()));
                wrapper.getChannel().writeAndFlush(container);
                response(packet, true);
            }, () -> response(packet, new WrapperNotFoundException("from server " + player.getProxyServer().getName())));
        }, () -> response(packet, new PlayerNotFoundException(playerUUID)));
    }
}
