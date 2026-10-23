package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.CloudException;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.UUID;

/**
 * A plugin changed player data ({@code setData}/{@code removeData} on a player or offline player): saved for online
 * and offline players alike.
 */
public class PlayerDataUpdateHandler implements ApiPacketHandler<Object> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_PLAYER_DATA_UPDATE;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<Object> packet) {
        var uuid = packet.getData()[0] instanceof UUID id ? id : UUID.fromString(String.valueOf(packet.getData()[0]));
        var key = (String) packet.getData()[1];
        var value = (byte[]) packet.getData()[2];
        RexlCloudMaster.getInstance().getPlayerManager().updateData(uuid, key, value, packet.getServer())
                .whenComplete((saved, e) -> {
                    if (e == null) {
                        response(packet, true);
                        return;
                    }
                    Console.error("Could not save the data %s of the player %s: %s", key, uuid, e.getMessage());
                    response(packet, new CloudException("Could not save the player data " + key));
                });
    }
}
