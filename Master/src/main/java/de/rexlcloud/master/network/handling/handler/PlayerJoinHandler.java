package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.api.Player;
import de.rexlcloud.core.api.events.events.player.PlayerCloudJoinEvent;
import de.rexlcloud.core.database.helper.QueryFilter;
import de.rexlcloud.core.database.helper.QueryType;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerCloudJoinPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.database.PlayerData;
import io.netty.channel.ChannelHandlerContext;

import java.sql.Timestamp;

public class PlayerJoinHandler implements IPacketHandler<PlayerCloudJoinPacket> {
    @Override
    public PacketType getType() {
        return PacketType.PLAYER_JOIN;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, PlayerCloudJoinPacket packet) {
        if (RexlCloudMaster.getInstance().getPlayerManager().getPlayer(packet.getUuid()).isPresent()) {
            // A proxy reporting its players again (see ServerAdoption); the master already has this one
            return;
        }
        RexlCloudMaster.getInstance().getServerManager().getServerByName(packet.getServerName())
                .ifPresent(playerServer -> {
                    RexlCloudMaster.getInstance().getServerManager().getServerByName(packet.getProxyServerName())
                            .ifPresent(proxyServer -> {
                                RexlCloudMaster.getInstance().getPlayerManager()
                                        .addPlayer(packet.getUuid(), packet.getName(), packet.getLogin(), playerServer, proxyServer)
                                        .thenAccept(player -> {
                                            playerServer.addOnlinePlayer(player);
                                            proxyServer.addOnlinePlayer(player);
                                            var event = new PlayerCloudJoinEvent(player);
                                            RexlCloudMaster.getInstance().getEventManager().callEvent(event);
                                            updateDatabase(player);
                                        });
                            });
                });
    }

    private void updateDatabase(Player player) {
        RexlCloudMaster.getInstance().getDatabaseConnection().getObjectHandler()
                .getDataFromClass(PlayerData.class, new QueryFilter(QueryType.WHERE_EQUALS_TO, "unique_id", player.getUuid()))
                .thenAccept(playerData -> {
                    if (playerData.isEmpty()) {
                        var newPlayerData = new PlayerData(0, player.getName(), player.getUuid(),
                                new Timestamp(player.getLogin()), new Timestamp(player.getLogin()), false);
                        RexlCloudMaster.getInstance()
                                .getDatabaseConnection().getObjectHandler()
                                .insertObject(newPlayerData)
                                .exceptionally(throwable -> {
                                    throwable.printStackTrace();
                                    return null;
                                });
                    } else {
                        var newPlayerData = playerData.get(0);
                        newPlayerData.setLastLogin(new Timestamp(player.getLogin()));
                        newPlayerData.setUsername(player.getName());
                        RexlCloudMaster.getInstance().getDatabaseConnection().getObjectHandler().updateObject(newPlayerData);
                    }
                })
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
                    return null;
                });
    }

}
