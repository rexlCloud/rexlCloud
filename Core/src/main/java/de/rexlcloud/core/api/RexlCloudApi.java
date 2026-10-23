package de.rexlcloud.core.api;

import de.rexlcloud.core.api.exceptions.*;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.CustomMessageChannelPacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.core.network.packets.api.ApiResponsePacket;
import de.rexlcloud.core.network.packets.api.ApiUpdateObjectPacket;
import de.rexlcloud.core.server.ServerGroup;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.NonNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Entry point of the rexlCloud API for plugins on cloud servers and proxies (Bridge, proxy plugin, MinestomBridge).
 * <p>
 * Get it with {@link #getInstance()} once the plugin has connected to its wrapper. It keeps a cache of all servers,
 * wrappers and players of the network that the cloud keeps up to date; the {@code getCached...} methods read it
 * directly, the methods returning a {@link CompletableFuture} either read the cache or ask the master.
 * <p>
 * Requests to the master complete exceptionally with {@link de.rexlcloud.core.api.exceptions.NoConnectionException}
 * while the plugin is not connected, {@link de.rexlcloud.core.api.exceptions.TimeOutException} after 10 seconds without
 * an answer and {@link de.rexlcloud.core.api.exceptions.ToManyRequestsException} if too many are pending.
 * <pre>{@code
 * RexlCloudApi.getInstance().getPlayer("Notch").thenAccept(player -> player.connect("Lobby-1"));
 * }</pre>
 */
public class RexlCloudApi {

    /**
     * The API of this server, {@code null} until the plugin connected to its wrapper the first time.
     */
    @Getter
    private static RexlCloudApi instance;

    private final ArrayList<Integer> requestIds = new ArrayList<>();

    /**
     * Message channels for sending own objects between servers (see {@link MessageChannelManager}).
     */
    @Getter
    private static final MessageChannelManager messageChannelManager = new MessageChannelManager();

    /**
     * Name of the server this plugin runs on, e.g. {@code Lobby-1}.
     */
    @Getter
    private final String serverName;

    /**
     * The connection to the wrapper; replaced when the plugin connects again (e.g. after the wrapper restarted).
     */
    @Getter
    private volatile Channel channel;
    private final HashMap<Integer, CompletableFuture<?>> futures;
    private final HashMap<String, Server> serverObjects = new HashMap<>();
    private final HashMap<String, Wrapper> wrapperObjects = new HashMap<>();
    private final HashMap<UUID, Player> playerObjects = new HashMap<>();


    /**
     * Internal: created by the cloud plugin when it connects to its wrapper; use {@link #getInstance()}.
     */
    public RexlCloudApi(Channel channel, int maxRequests, String serverName) {
        if (instance != null) {
            this.channel = null;
            this.futures = null;
            this.serverName = null;
            return;
        }
        instance = this;
        this.channel = channel;
        this.serverName = serverName;
        this.futures = new HashMap<>();
        for (int i = 0; i < maxRequests; i++) {
            requestIds.add(i);
        }
    }

    /**
     * The plugin's connection to its wrapper was re-established: requests go over the new one.
     */
    public void reconnect(Channel channel) {
        this.channel = channel;
    }

    /**
     * The API of this server: the existing one with the new connection, or a new one on the first connection.
     */
    public static RexlCloudApi connect(Channel channel, int maxRequests, String serverName) {
        if (instance != null) {
            instance.reconnect(channel);
            return instance;
        }
        return new RexlCloudApi(channel, maxRequests, serverName);
    }

    /**
     * @return names of all known wrappers
     */
    public List<String> getAllWrapperNames() {
        return wrapperObjects.values().stream().map(Wrapper::getName).toList();
    }

    /**
     * @return names of all servers and proxies of the network
     */
    public List<String> getAllServerNames() {
        return serverObjects.values().stream().map(Server::getName).toList();
    }

    /**
     * Internal: called by the cloud plugin for every packet from the wrapper; keeps the cache up to date and
     * completes requests.
     */
    public void receivePacket(PacketContainer packetContainer) {
        if (packetContainer.getPacket() instanceof CustomMessageChannelPacket packet) {
            getMessageChannelManager().getMessageChannels().values().stream().toList().stream()
                    .filter(messageChannel -> messageChannel.getChannel().equals(packet.getMessageChannel()))
                    .forEach(messageChannel -> messageChannel.handelPacket(packetContainer));
        } else if (packetContainer.getPacket() instanceof ApiResponsePacket packet) {
            if (!packet.getServer().equals(serverName)) {
                return;
            }
            if (packet.getResponse() instanceof Throwable) {
                this.futures.remove(packet.getRequestId()).completeExceptionally((Throwable) packet.getResponse());
            } else {
                ((CompletableFuture<Object>) this.futures.remove(packet.getRequestId())).complete(packet.getResponse());
            }
            if (!requestIds.contains(packet.getRequestId())) {
                requestIds.add(packet.getRequestId());
            }
        } else if (packetContainer.getPacket() instanceof ApiUpdateObjectPacket packet) {
            switch (packetContainer.getPacketType()) {
                case API_UPDATE_SERVER_OBJECT -> {
                    var server = ((Server) packet.getObject());
                    if (server == null) {
                        serverObjects.remove(packet.getName());
                        return;
                    }
                    if (!wrapperObjects.containsKey(server.getWrapper().getName())) {
                        wrapperObjects.put(server.getWrapper().getName(), server.getWrapper());
                    }
                    var wrapper = wrapperObjects.get(server.getWrapper().getName());
                    if (serverObjects.containsKey(packet.getName())) {
                        serverObjects.get(packet.getName()).update(server, wrapper);
                    } else {
                        server.updateWrapper(wrapper);
                        serverObjects.put(packet.getName(), server);
                    }
                }
                case API_UPDATE_WRAPPER_OBJECT -> {
                    var wrapper = ((Wrapper) packet.getObject());
                    if (wrapper == null) {
                        wrapperObjects.remove(packet.getName());
                        return;
                    }
                    if (wrapperObjects.containsKey(packet.getName())) {
                        wrapperObjects.get(packet.getName()).update(wrapper);
                    } else {
                        wrapperObjects.put(packet.getName(), wrapper);
                    }
                }
                case API_UPDATE_PLAYER_OBJECT -> {
                    var player = ((Player) packet.getObject());
                    var playerUUID = UUID.fromString(packet.getName());
                    if (player == null) {
                        playerObjects.remove(playerUUID);
                        return;
                    }
                    if (playerObjects.containsKey(playerUUID)) {
                        playerObjects.get(playerUUID).update(player, serverObjects.get(player.getServer().getName()));
                    } else {
                        player.update(player, serverObjects.get(player.getServer().getName()));
                        playerObjects.put(playerUUID, player);
                    }
                }
                case API_UPDATE_PLAYER_DATA -> {
                    var object = (Object[]) packet.getObject();
                    var playerUUID = UUID.fromString(packet.getName());
                    var key = object[0].toString();
                    var data = (byte[]) object[1];
                    if (playerObjects.containsKey(playerUUID)) {
                        playerObjects.get(playerUUID).updateData(key, data);
                    }
                }

            }
        }
    }

    /**
     * Runs a console command on a server.
     *
     * @param server  the server
     * @param command the command, without a leading slash
     * @return completes with {@code true} once the server received it
     */
    public CompletableFuture<Boolean> executeOnServer(@NonNull Server server, @NonNull String command) {
        return createRequest(PacketType.API_REQUEST_EXECUTE_ON_SERVER, server.getName(), command);
    }

    /**
     * @return all groups, asked from the master
     */
    public CompletableFuture<ServerGroup[]> getServerGroups() {
        return createRequest(PacketType.API_REQUEST_SERVER_GROUPS);
    }

    /**
     * @param serverGroupName name of the group
     * @return the group, asked from the master; completes exceptionally with
     *         {@link de.rexlcloud.core.api.exceptions.ServerGroupNotFoundException} if it does not exist
     */
    public CompletableFuture<ServerGroup> getServerGroup(@NonNull String serverGroupName) {
        return createRequest(PacketType.API_REQUEST_SERVER_GROUP, serverGroupName);
    }

    /**
     * @param wrapperName name of the wrapper, e.g. {@code Wrapper-1}
     * @return the wrapper from the cache; completes exceptionally with
     *         {@link de.rexlcloud.core.api.exceptions.WrapperNotFoundException} if it is unknown
     */
    public CompletableFuture<Wrapper> getWrapper(String wrapperName) {
        var future = new CompletableFuture<Wrapper>();
        var server = wrapperObjects.get(wrapperName);
        if (server == null) {
            future.completeExceptionally(new WrapperNotFoundException(wrapperName));
        } else {
            future.complete(server);
        }
        return future;
    }

    /**
     * @return all known wrappers, from the cache
     */
    public CompletableFuture<Wrapper[]> getWrappers() {
        var future = new CompletableFuture<Wrapper[]>();
        var wrappers = wrapperObjects.values().toArray(Wrapper[]::new);
        future.complete(wrappers);
        return future;
    }

    /**
     * Shuts a wrapper down; it stops its servers.
     *
     * @param wrapper the wrapper
     * @return completes once the master accepted the request
     */
    public CompletableFuture<Boolean> stopWrapper(Wrapper wrapper) {
        return createRequest(PacketType.API_REQUEST_WRAPPER_STOP, wrapper.getName());
    }

    /**
     * Stops a server. If its group needs it for its minimum, the cloud starts a new one.
     *
     * @param server the server
     * @return completes once the master accepted the request
     */
    public CompletableFuture<Boolean> stopServer(Server server) {
        return stopServer(server.getName());
    }

    /**
     * Stops a server. If its group needs it for its minimum, the cloud starts a new one.
     *
     * @param serverName name of the server
     * @return completes once the master accepted the request
     */
    public CompletableFuture<Boolean> stopServer(String serverName) {
        return createRequest(PacketType.API_REQUEST_STOP_SERVER, serverName);
    }

    /**
     * @param uuid UUID of an online player
     * @return the player from the cache; completes exceptionally with
     *         {@link de.rexlcloud.core.api.exceptions.PlayerNotFoundException} if no such player is online
     */
    public CompletableFuture<Player> getPlayer(UUID uuid) {
        var future = new CompletableFuture<Player>();
        var player = playerObjects.get(uuid);
        if (player == null) {
            future.completeExceptionally(new PlayerNotFoundException(uuid));
        } else {
            future.complete(player);
        }
        return future;
    }

    /**
     * @param name name of an online player (case-sensitive)
     * @return the player from the cache; completes exceptionally with
     *         {@link de.rexlcloud.core.api.exceptions.PlayerNotFoundException} if no such player is online
     */
    public CompletableFuture<Player> getPlayer(String name) {
        var future = new CompletableFuture<Player>();
        var player = playerObjects.values().stream().filter(player1 -> player1.getName().equals(name)).findFirst().orElse(null);
        if (player == null) {
            future.completeExceptionally(new PlayerNotFoundException(name));
        } else {
            future.complete(player);
        }
        return future;
    }

    /**
     * @return all players online on the network, from the cache
     */
    public CompletableFuture<Player[]> getPlayers() {
        var future = new CompletableFuture<Player[]>();
        var players = playerObjects.values().toArray(Player[]::new);
        future.complete(players);
        return future;
    }

    /**
     * @return a copy of the cached online players
     */
    public ArrayList<Player> getCachedPlayers() {
        return new ArrayList<>(playerObjects.values());
    }

    /**
     * @param server a server
     * @return the players on it, from the cache
     */
    public CompletableFuture<Player[]> getPlayersOnServer(Server server) {
        var future = new CompletableFuture<Player[]>();
        var wrappers = playerObjects.values().stream().filter(player -> player.getServer().equals(server)).toArray(Player[]::new);
        future.complete(wrappers);
        return future;
    }

    /**
     * @param serverGroup a group
     * @return the players on its servers, from the cache
     */
    public CompletableFuture<Player[]> getPlayersOnServerGroup(ServerGroup serverGroup) {
        var future = new CompletableFuture<Player[]>();
        var wrappers = playerObjects.values().stream().filter(player -> player.getServer().getServerGroup().equals(serverGroup)).toArray(Player[]::new);
        future.complete(wrappers);
        return future;
    }

    /**
     * Starts one more server of a group.
     *
     * @param serverGroup the group
     * @return the new server; it is starting, {@link Server#isOnline()} turns {@code true} once it is up
     */
    public CompletableFuture<Server> startServer(ServerGroup serverGroup) {
        return startServer(serverGroup, 1).thenApply(servers -> servers[0]);
    }

    /**
     * Starts more servers of a group.
     *
     * @param serverGroup the group
     * @param amount      how many
     * @return the new servers, which are starting
     */
    public CompletableFuture<Server[]> startServer(ServerGroup serverGroup, int amount) {
        return createRequest(PacketType.API_REQUEST_START_SERVER, serverGroup.getName(), amount + "");
    }

    /**
     * @param serverName name of a server or proxy, e.g. {@code Lobby-1}
     * @return the server from the cache; completes exceptionally with
     *         {@link de.rexlcloud.core.api.exceptions.ServerNotFoundException} if it is unknown
     */
    public CompletableFuture<Server> getServer(String serverName) {
        var future = new CompletableFuture<Server>();
        var server = serverObjects.get(serverName);
        if (server == null) {
            future.completeExceptionally(new ServerNotFoundException(serverName));
        } else {
            future.complete(server);
        }
        return future;
    }

    /**
     * @return a copy of the cached servers and proxies
     */
    public ArrayList<Server> getCachedServers() {
        return new ArrayList<>(serverObjects.values());
    }

    /**
     * @param serverName name of a server or proxy
     * @return the server from the cache, empty if it is unknown
     */
    public Optional<Server> getCachedServer(String serverName) {
        return serverObjects.values().stream().filter(server -> server.getName().equals(serverName)).findAny();
    }

    /**
     * @return all servers and proxies, from the cache
     */
    public CompletableFuture<Server[]> getAllServers() {
        var future = new CompletableFuture<Server[]>();
        var servers = serverObjects.values().stream().toArray(Server[]::new);
        future.complete(servers);
        return future;
    }

    /**
     * @param serverGroup a group
     * @return its servers, asked from the master
     */
    public CompletableFuture<Server[]> getAllServersByGroup(ServerGroup serverGroup) {
        return createRequest(PacketType.API_REQUEST_GET_SERVERS, serverGroup.getName());
    }

    /**
     * A player who joined the network at least once, online or not, with the data plugins stored for them.
     *
     * @param uuid UUID of the player
     * @return the player as the master stored them; completes exceptionally with
     *         {@link de.rexlcloud.core.api.exceptions.PlayerNotFoundException} if they never joined
     */
    public CompletableFuture<CloudOfflinePlayer> getOfflinePlayer(@NonNull UUID uuid) {
        return createRequest(PacketType.API_REQUEST_GET_OFFLINE_PLAYER, uuid);
    }

    /**
     * A player who joined the network at least once, online or not, with the data plugins stored for them.
     *
     * @param name the name the player had when they last joined (case-insensitive)
     * @return the player as the master stored them; completes exceptionally with
     *         {@link de.rexlcloud.core.api.exceptions.PlayerNotFoundException} if no player joined with this name
     */
    public CompletableFuture<CloudOfflinePlayer> getOfflinePlayer(@NonNull String name) {
        return createRequest(PacketType.API_REQUEST_GET_OFFLINE_PLAYER, name);
    }

    /**
     * Internal: sends changed player data ({@code null} removes it) to the master, which saves it and passes it on
     * to the other servers.
     */
    protected CompletableFuture<Boolean> sendPlayerDataUpdate(PlayerDataHolder holder, String key, byte[] bytes) {
        var cached = playerObjects.get(holder.getUuid());
        if (cached != null && cached != holder) {
            // Changed through a CloudOfflinePlayer while the player is online here; the master skips this server
            cached.updateData(key, bytes);
        }
        return createRequest(PacketType.API_REQUEST_PLAYER_DATA_UPDATE, holder.getUuid(), key, bytes);
    }

    /**
     * Internal: sends a request to the master and completes with its answer.
     */
    protected <T, Z> CompletableFuture<T> createRequest(PacketType packetType, Z... data) {
        var future = new CompletableFuture<T>();
        if (requestIds.isEmpty()) {
            future.completeExceptionally(new ToManyRequestsException());
            return future;
        }
        var requestId = requestIds.remove(0);
        var apiRequestPacket = new ApiRequestPacket<>(requestId, serverName, data);
        var packetContainer = new PacketContainer(packetType, apiRequestPacket);
        this.futures.put(requestId, future);
        if (channel != null && channel.isOpen()) {
            channel.writeAndFlush(packetContainer);
        } else {
            future.completeExceptionally(new NoConnectionException());
            requestIds.add(requestId);
            return future;
        }
        new Thread(() -> new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (!future.isDone()) {
                    future.completeExceptionally(new TimeOutException());
                    if (!requestIds.contains(requestId)) {
                        requestIds.add(requestId);
                    }
                }
            }
        }, 10000)).start();
        return future;
    }

}
