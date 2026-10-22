package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.PlayerNotFoundException;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.core.network.packets.api.ApiUpdateObjectPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.UUID;

public class PlayerDataUpdateHandler implements ApiPacketHandler<Object> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_PLAYER_DATA_UPDATE;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<Object> packet) {
        var uuid = UUID.fromString((String) packet.getData()[0]);
        var key = (String) packet.getData()[1];
        var value = (byte[]) packet.getData()[2];
        var data = new Object[]{key, value};
        RexlCloudMaster.getInstance().getPlayerManager().getPlayer(uuid).ifPresentOrElse(player -> {
            if (value == null) {
                player.removeData(key);
            } else {
                player.setData(key, value);
            }
            RexlCloudMaster.getInstance().getServerManager().getServers().stream()
                    .filter(server1 -> !server1.getName().equals(player.getServer().getName()))
                    .forEach(server1 -> {
                        ApiUpdateObjectPacket apiUpdateObjectPacket = new ApiUpdateObjectPacket(server1.getName(), player.getUuid().toString(), data);
                        PacketContainer packetContainerUpdateObjects = new PacketContainer(PacketType.API_UPDATE_PLAYER_DATA, apiUpdateObjectPacket);
                        server1.sendPacket(packetContainerUpdateObjects);
                    });
        }, () -> response(packet, new PlayerNotFoundException(uuid)));
    }
}
