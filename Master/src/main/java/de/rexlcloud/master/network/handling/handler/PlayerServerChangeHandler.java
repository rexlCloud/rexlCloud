package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.api.events.events.player.PlayerServerChangeEvent;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerServerChangePacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.master.RexlCloudMaster;
import io.netty.channel.ChannelHandlerContext;

public class PlayerServerChangeHandler implements IPacketHandler<PlayerServerChangePacket> {
    @Override
    public PacketType getType() {
        return PacketType.PLAYER_SERVER_CHANGE;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, PlayerServerChangePacket packet) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        serverManager.getServerByName(packet.getServer()).ifPresent(serverByName ->
                RexlCloudMaster.getInstance().getPlayerManager().getPlayer(packet.getUuid()).ifPresent(player -> {
                    var oldServer = player.getServer();
                    RexlCloudMaster.getInstance().getPlayerManager().updateServer(player, serverByName);
                    var event = new PlayerServerChangeEvent(player, oldServer, player.getServer());
                    RexlCloudMaster.getInstance().getEventManager().callEvent(event);
                    RexlCloudMaster.getInstance().getServerManager().getServerByApiServer(oldServer)
                            .ifPresent(server -> server.removeOnlinePlayer(player));
                    serverByName.addOnlinePlayer(player);
                }));
    }
}
