package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.api.events.events.player.PlayerCloudQuitEvent;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerProxyDisconnectPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.master.RexlCloudMaster;
import io.netty.channel.ChannelHandlerContext;
import lombok.val;

public class PlayerQuitHandler implements IPacketHandler<PlayerProxyDisconnectPacket> {
    @Override
    public PacketType getType() {
        return PacketType.PLAYER_QUIT;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, PlayerProxyDisconnectPacket packet) {
        val player = RexlCloudMaster.getInstance().getPlayerManager().removePlayer(packet.getUuid());
        if (player == null) {
            return;
        }
        var event = new PlayerCloudQuitEvent(player);
        RexlCloudMaster.getInstance().getEventManager().callEvent(event);

        RexlCloudMaster.getInstance().getServerManager().getServerByApiServer(player.getServer()).ifPresent(server ->
                server.removeOnlinePlayer(player));

        RexlCloudMaster.getInstance().getServerManager().getServerByApiServer(player.getProxyServer()).ifPresent(server ->
                server.removeOnlinePlayer(player));
    }


}
