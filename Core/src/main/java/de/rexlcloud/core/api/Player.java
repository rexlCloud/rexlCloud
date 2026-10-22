package de.rexlcloud.core.api;

import de.rexlcloud.core.network.PacketType;
import lombok.Getter;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A player who is online on the network: on which proxy and server, and since when.
 * <p>
 * The object is updated by the cloud while the player is online (e.g. {@link #getServer()} after a server switch).
 * Actions like {@link #connect(String)} or {@link #sendMessage(Component)} are carried out by the player's proxy.
 * Players can also have data that plugins store for them in the cloud's database ({@link #setData}); it stays when
 * they leave and is available on every server.
 */
@Getter
public class Player implements Serializable {

    /**
     * The player's name.
     */
    @NonNull
    private final String name;
    /**
     * The player's UUID.
     */
    @NotNull
    private final UUID uuid;
    /**
     * The server the player is on.
     */
    @NonNull
    private Server server;
    /**
     * The proxy the player is connected through.
     */
    @NonNull
    private final Server proxyServer;
    /**
     * When the player joined the network, in milliseconds since the epoch.
     */
    private long login;
    /**
     * The stored data entries of the player; read them with {@link #getData(String)}.
     */
    private final ArrayList<PlayerDatabaseData> data;

    /**
     * Internal: players come from {@link RexlCloudApi}.
     */
    public Player(@NonNull String name, @NotNull UUID uuid, @NonNull Server server, @NonNull Server proxyServer, long login, ArrayList<PlayerDatabaseData> data) {
        this.name = name;
        this.uuid = uuid;
        this.server = server;
        this.proxyServer = proxyServer;
        this.login = login;
        this.data = data;
    }

    /**
     * Internal: applies a newer state of the player.
     */
    protected void update(@NonNull Player player, @NonNull Server server) {
        this.server = server;
        this.login = player.getLogin();
    }

    private static final StackWalker CALLER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * Data a plugin stored for this player. Only JDK value types and classes of the calling plugin are read; data
     * of other types is treated as missing.
     */
    @SuppressWarnings("unchecked")
    public <T extends Serializable> Optional<T> getData(String key) {
        var caller = CALLER.getCallerClass().getClassLoader();
        return data.stream().filter(playerDatabaseData -> playerDatabaseData.getKey().equals(key)).findAny()
                .map(playerDatabaseData -> {
                    try {
                        return (T) SafeObjectInput.read(playerDatabaseData.getValue(), caller);
                    } catch (IOException | ClassNotFoundException | ClassCastException e) {
                        System.err.println("[rexlCloud] Could not read the player data " + key + " of " + name
                                + ": " + e.getMessage());
                        return null;
                    }
                });
    }

    /**
     * Stores data for the player in the cloud's database, replacing a value with the same key. The value is
     * serialized with Java serialization; only JDK value types (strings, numbers, collections, time types) and classes of
     * the plugin itself can be read back with {@link #getData(String)}.
     *
     * @param key   name of the value, e.g. {@code "coins"}
     * @param value the value
     * @param <T>   type of the value
     * @return {@code false} if the value could not be serialized
     */
    public <T extends Serializable> boolean setData(@NonNull String key, @NonNull T value) {
        try {
            var baos = new ByteArrayOutputStream();
            var oos = new ObjectOutputStream(baos);
            oos.writeObject(value);
            var bytes = baos.toByteArray();

            this.data.stream().filter(playerDatabaseData -> playerDatabaseData.getKey().equals(key)).findAny()
                    .ifPresentOrElse(playerDatabaseData -> playerDatabaseData.setValue(bytes),
                            () -> data.add(new PlayerDatabaseData(uuid, key, bytes)));

            RexlCloudApi.getInstance().sendPlayerDataUpdate(this, key, bytes);
        } catch (Exception e) {
            System.out.println("Could not serialize data");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * @param key name of a value
     * @return whether the player has data under this key
     */
    public boolean hasData(String key) {
       return data.stream().anyMatch(playerDatabaseData -> playerDatabaseData.getKey().equals(key));
    }

    /**
     * Removes the player's data under this key from the database.
     *
     * @param key name of the value
     */
    public void removeData(String key) {
        data.removeIf(playerDatabaseData -> playerDatabaseData.getKey().equals(key));
        RexlCloudApi.getInstance().sendPlayerDataUpdate(this, key, null);
    }

    /**
     * Internal: applies data changed on another server.
     */
    protected void updateData(String key, byte[] data) {
        if (data == null) {
            this.data.removeIf(playerDatabaseData -> playerDatabaseData.getKey().equals(key));
            return;
        }
        this.data.stream().filter(playerDatabaseData -> playerDatabaseData.getKey().equals(key)).findAny()
                .ifPresentOrElse(playerDatabaseData -> playerDatabaseData.setValue(data),
                        () -> this.data.add(new PlayerDatabaseData(uuid, key, data)));
    }

    /**
     * Disconnects the player from the network.
     *
     * @param message reason shown to the player (MiniMessage or {@code &} colour codes), {@code null} for none
     * @return completes once the proxy received the request
     */
    public CompletableFuture<Boolean> kick(@Nullable String message) {
        return RexlCloudApi.getInstance().createRequest(PacketType.API_KICK_PLAYER, uuid.toString(), message == null ? "" : message);
    }

    /**
     * Sends a plain text message to the player.
     *
     * @param message the text
     * @return completes once the proxy received the request
     */
    public CompletableFuture<Boolean> sendMessage(@Nullable String message) {
        return sendMessage(message == null ? Component.empty() : Component.text(message));
    }

    /**
     * Sends a chat message to the player, e.g. one built with Adventure or {@link CloudText#parse(String)}.
     *
     * @param message the message
     * @return completes once the proxy received the request
     */
    public CompletableFuture<Boolean> sendMessage(@Nullable Component message) {
        return RexlCloudApi.getInstance()
                .createRequest(PacketType.API_SEND_MESSAGE_TO_PLAYER, uuid.toString(), GsonComponentSerializer.gson()
                        .serialize(message == null ? Component.empty() : message));
    }

    /**
     * Sends the player to another server.
     *
     * @param server the target server
     * @return completes once the proxy received the request
     */
    public CompletableFuture<Boolean> connect(@NonNull Server server) {
        return RexlCloudApi.getInstance().createRequest(PacketType.API_PLAYER_CHANGE_SERVER, uuid.toString(), server.getName());
    }

    /**
     * Sends the player to another server.
     *
     * @param serverName name of the target server
     * @return completes once the proxy received the request
     */
    public CompletableFuture<Boolean> connect(@NonNull String serverName) {
        return RexlCloudApi.getInstance().createRequest(PacketType.API_PLAYER_CHANGE_SERVER, uuid.toString(), serverName);
    }

    /**
     * Sends the player to a fallback server (a server of a group marked as fallback, e.g. a lobby).
     *
     * @return completes once the proxy received the request
     */
    public CompletableFuture<Boolean> connectToFallbackServer() {
        return RexlCloudApi.getInstance().createRequest(PacketType.API_PLAYER_CHANGE_SERVER, uuid.toString(), null);
    }

    /**
     * Sends the player to another server and teleports them there once they arrived.
     *
     * @param server         the target server
     * @param serverLocation where on it
     * @return completes once the proxy received the request
     */
    public CompletableFuture<Boolean> connectAndTeleport(@NonNull Server server, @NonNull ServerLocation serverLocation) {
        return RexlCloudApi.getInstance().createRequest(PacketType.API_PLAYER_CHANGE_SERVER_AND_TELEPORT, uuid.toString(), server.getName(), serverLocation);
    }

    /**
     * Sends the player to another server and teleports them there once they arrived.
     *
     * @param serverName     name of the target server
     * @param serverLocation where on it
     * @return completes once the proxy received the request
     */
    public CompletableFuture<Boolean> connectAndTeleport(@NonNull String serverName, @NonNull ServerLocation serverLocation) {
        return RexlCloudApi.getInstance().createRequest(PacketType.API_PLAYER_CHANGE_SERVER_AND_TELEPORT, uuid.toString(), serverName, serverLocation);
    }

    /**
     * Teleports the player on the server they are on.
     *
     * @param serverLocation the target; its world name selects the world
     * @return completes once the server received the request
     */
    public CompletableFuture<Boolean> teleport(ServerLocation serverLocation) {
        return RexlCloudApi.getInstance().createRequest(PacketType.API_PLAYER_TELEPORT, uuid.toString(), serverLocation);
    }

    /**
     * @return where the player is, asked from their server
     */
    public CompletableFuture<ServerLocation> getLocation() {
        return RexlCloudApi.getInstance().createRequest(PacketType.API_REQUEST_GET_PLAYER_LOCATION, uuid.toString());
    }

}
