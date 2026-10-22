package de.rexlcloud.master.player;

import de.rexlcloud.core.api.Player;
import de.rexlcloud.core.api.PlayerDatabaseData;
import de.rexlcloud.core.api.Server;
import de.rexlcloud.core.database.helper.QueryFilter;
import de.rexlcloud.core.database.helper.QueryType;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiUpdateObjectPacket;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.master.RexlCloudMaster;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlayerManager {

    private final HashMap<UUID, Player> players;

    public PlayerManager() {
        players = new HashMap<>();
    }

    @NotNull
    public CompletableFuture<Player> addPlayer(UUID uuid, String name, long join, de.rexlcloud.master.server.Server server, de.rexlcloud.master.server.Server proxyServer) {
        var future = new CompletableFuture<Player>();
        RexlCloudMaster.getInstance().getDatabaseConnection().getObjectHandler().getDataFromClass(PlayerDatabaseData.class,
                new QueryFilter(QueryType.WHERE_EQUALS_TO, "unique_id", uuid)).thenAccept(data -> {
            var player = new Player(name, uuid,
                    new Server(server, server.getWrapper().getApiWrapper()),
                    new Server(proxyServer, server.getWrapper().getApiWrapper()),
                    join, data);
            players.put(uuid, player);
            sendUpdate(player);
            future.complete(player);
        });
        return future;
    }

    public ArrayList<Player> getPlayers() {
        return new ArrayList<>(players.values());
    }

    @NotNull
    public Player updateServer(Player player, de.rexlcloud.master.server.Server server) {
        var newPlayer = new Player(player.getName(), player.getUuid(), new Server(server,
                server.getWrapper().getApiWrapper()), player.getProxyServer(), player.getLogin(), player.getData());
        players.put(player.getUuid(), newPlayer);
        sendUpdate(newPlayer);
        return newPlayer;
    }

    @Nullable
    public Player removePlayer(UUID uuid) {
        var remove = players.remove(uuid);
        sendRemove(uuid);
        return remove;
    }

    public Optional<Player> getPlayer(UUID uuid) {
        return Optional.ofNullable(players.getOrDefault(uuid, null));
    }

    public void removePlayer(Player player) {
        removePlayer(player.getUuid());
    }

    private void sendUpdate(Player player) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        serverManager.getServers().stream().filter(AbstractServer::isStarted).forEach(serverOther -> {
            var apiUpdateObjectPacket = new ApiUpdateObjectPacket(serverOther.getName(), player.getUuid().toString(), player);
            var packetContainerUpdateObjects = new PacketContainer(PacketType.API_UPDATE_PLAYER_OBJECT, apiUpdateObjectPacket);
            serverOther.sendPacket(packetContainerUpdateObjects);
        });
    }

    private void sendRemove(UUID uuid) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        serverManager.getServers().stream().filter(AbstractServer::isStarted).forEach(serverOther -> {
            var apiUpdateObjectPacket = new ApiUpdateObjectPacket(serverOther.getName(), uuid.toString(), null);
            var packetContainerUpdateObjects = new PacketContainer(PacketType.API_UPDATE_PLAYER_OBJECT, apiUpdateObjectPacket);
            if (serverOther.getWrapper() != null && serverOther.getWrapper().getChannel() != null) {
                serverOther.getWrapper().getChannel().writeAndFlush(packetContainerUpdateObjects);
            }
        });
    }

}
