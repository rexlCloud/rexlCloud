package de.rexlcloud.master.player;

import de.rexlcloud.core.api.CloudOfflinePlayer;
import de.rexlcloud.core.api.Player;
import de.rexlcloud.core.api.Server;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.database.DatabaseObjectHandler;
import de.rexlcloud.core.database.helper.QueryFilter;
import de.rexlcloud.core.database.helper.QueryType;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiUpdateObjectPacket;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.database.PlayerData;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlayerManager {

    private final HashMap<UUID, Player> players;
    private final DatabaseObjectHandler database;
    @Getter
    private final PlayerDataStore dataStore;

    public PlayerManager(DatabaseObjectHandler database) {
        players = new HashMap<>();
        this.database = database;
        dataStore = new PlayerDataStore(database);
    }

    @NotNull
    public CompletableFuture<Player> addPlayer(UUID uuid, String name, long join, de.rexlcloud.master.server.Server server, de.rexlcloud.master.server.Server proxyServer) {
        var future = new CompletableFuture<Player>();
        dataStore.load(uuid).exceptionally(e -> {
            Console.error("Could not load the data of the player %s: %s", name, e.getMessage());
            return new ArrayList<>();
        }).thenAccept(data -> {
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

    /**
     * Saves changed player data ({@code null} removes it), whether the player is online or not. If they are, the
     * change is applied to their player and sent to every started server except {@code sourceServer}, which
     * already has it.
     *
     * @return completes once the change is saved
     */
    public CompletableFuture<Void> updateData(UUID uuid, String key, byte[] value, @Nullable String sourceServer) {
        getPlayer(uuid).ifPresent(player -> {
            player.updateData(key, value);
            var data = new Object[]{key, value};
            RexlCloudMaster.getInstance().getServerManager().getServers().stream()
                    .filter(AbstractServer::isStarted)
                    .filter(server -> !server.getName().equals(sourceServer))
                    .forEach(server -> server.sendPacket(new PacketContainer(PacketType.API_UPDATE_PLAYER_DATA,
                            new ApiUpdateObjectPacket(server.getName(), uuid.toString(), data))));
        });
        return dataStore.save(uuid, key, value);
    }

    /**
     * A player who joined the network at least once, with their stored data.
     *
     * @return empty if the player never joined
     */
    public CompletableFuture<Optional<CloudOfflinePlayer>> getOfflinePlayer(UUID uuid) {
        return offlinePlayer(new QueryFilter(QueryType.WHERE_EQUALS_TO, "unique_id", uuid));
    }

    /**
     * A player who joined the network at least once, by the name they had when they last joined (case-insensitive).
     * If several players joined with that name, the one who joined last.
     *
     * @return empty if no player joined with this name
     */
    public CompletableFuture<Optional<CloudOfflinePlayer>> getOfflinePlayer(String name) {
        return offlinePlayer(new QueryFilter(QueryType.WHERE_EQUALS_IGNORE_CASE, "username", name));
    }

    private CompletableFuture<Optional<CloudOfflinePlayer>> offlinePlayer(QueryFilter filter) {
        return database.getDataFromClass(PlayerData.class, filter).thenCompose(rows -> {
            var row = rows.stream().max(Comparator.comparing(PlayerData::getLastLogin,
                    Comparator.nullsFirst(Comparator.naturalOrder())));
            if (row.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.<CloudOfflinePlayer>empty());
            }
            var playerData = row.get();
            return dataStore.load(playerData.getUniqueId()).thenApply(data -> Optional.of(new CloudOfflinePlayer(
                    playerData.getUsername(), playerData.getUniqueId(), millis(playerData.getFirstLogin()),
                    millis(playerData.getLastLogin()), players.containsKey(playerData.getUniqueId()), data)));
        });
    }

    private static long millis(@Nullable Timestamp timestamp) {
        return timestamp == null ? 0 : timestamp.getTime();
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
