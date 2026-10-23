package de.rexlcloud.core.api;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.UUID;

/**
 * A player who joined the network at least once, as the master stored them, whether they are online or not. Get
 * one with {@link RexlCloudApi#getOfflinePlayer(UUID)} or {@link RexlCloudApi#getOfflinePlayer(String)}.
 * <p>
 * It holds the same data plugins store with {@link Player#setData}: {@link #getData(String)} reads it as it was
 * when the player was requested, and {@link #setData}/{@link #removeData} save changes in the cloud's database. If
 * the player is online, the change also reaches their {@link Player} on every server.
 * <pre>{@code
 * RexlCloudApi.getInstance().getOfflinePlayer("Notch").thenAccept(player -> {
 *     int coins = player.<Integer>getData("coins").orElse(0);
 *     player.setData("coins", coins + 100);
 * });
 * }</pre>
 */
@Getter
public class CloudOfflinePlayer extends PlayerDataHolder {

    /**
     * The name the player had when they last joined.
     */
    @NonNull
    private final String name;
    /**
     * The player's UUID.
     */
    @NonNull
    private final UUID uuid;
    /**
     * When the player first joined the network, in milliseconds since the epoch.
     */
    private final long firstLogin;
    /**
     * When the player last joined the network, in milliseconds since the epoch.
     */
    private final long lastLogin;
    /**
     * Whether the player was online when this object was requested.
     */
    private final boolean online;
    /**
     * The stored data entries of the player; read them with {@link #getData(String)}.
     */
    private final ArrayList<PlayerDatabaseData> data;

    /**
     * Internal: offline players come from {@link RexlCloudApi}.
     */
    public CloudOfflinePlayer(@NonNull String name, @NonNull UUID uuid, long firstLogin, long lastLogin, boolean online,
                              ArrayList<PlayerDatabaseData> data) {
        this.name = name;
        this.uuid = uuid;
        this.firstLogin = firstLogin;
        this.lastLogin = lastLogin;
        this.online = online;
        this.data = data == null ? new ArrayList<>() : data;
    }

}
