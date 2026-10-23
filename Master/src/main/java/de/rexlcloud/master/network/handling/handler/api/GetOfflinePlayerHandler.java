package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.CloudException;
import de.rexlcloud.core.api.exceptions.PlayerNotFoundException;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.UUID;

/**
 * {@code RexlCloudApi.getOfflinePlayer}: a player who joined at least once, by UUID or name, with their stored data.
 */
public class GetOfflinePlayerHandler implements ApiPacketHandler<Object> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_GET_OFFLINE_PLAYER;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<Object> packet) {
        var target = packet.getData()[0];
        var playerManager = RexlCloudMaster.getInstance().getPlayerManager();
        var lookup = target instanceof UUID uuid
                ? playerManager.getOfflinePlayer(uuid)
                : playerManager.getOfflinePlayer(String.valueOf(target));
        lookup.whenComplete((player, e) -> {
            if (e != null) {
                Console.error("Could not load the player %s: %s", target, e.getMessage());
                response(packet, new CloudException("Could not load the player " + target));
            } else if (player.isPresent()) {
                response(packet, player.get());
            } else {
                response(packet, target instanceof UUID uuid
                        ? new PlayerNotFoundException(uuid) : new PlayerNotFoundException(String.valueOf(target)));
            }
        });
    }
}
