package de.rexlcloud.bridge;

import com.google.gson.JsonObject;
import de.rexlcloud.bridge.listener.PlayerJoinListener;
import de.rexlcloud.bridge.networking.ServerClient;
import de.rexlcloud.core.api.Player;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.WrapperReconnect;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.UUID;

/**
 * The rexlCloud plugin for Paper and Spigot servers (1.8.8 and newer), installed by the cloud on every server.
 * <p>
 * Other plugins use the cloud through {@link RexlCloudApi#getInstance()} (or {@link #getRexlCloudApi()}); add
 * {@code depend: [rexlCloudBridge]} to their plugin.yml so the Bridge is enabled first. Positions are converted with
 * {@link de.rexlcloud.bridge.helper.RexlCloudHelper}.
 * <p>
 * The Bridge connects to its wrapper on 127.0.0.1; if the wrapper goes away it tries to connect again for two
 * minutes and then stops the server.
 */
public class Bridge extends JavaPlugin {

    /**
     * The plugin instance.
     */
    @Getter
    private static Bridge instance;
    private ServerClient serverClient;
    /**
     * Internal: connects again when the connection to the wrapper breaks.
     */
    @Getter
    private WrapperReconnect wrapperReconnect;
    /**
     * The cloud API, {@code null} until the first connection to the wrapper.
     */
    @Getter
    @Setter
    private RexlCloudApi rexlCloudApi;

    /**
     * Name of this server in the cloud, e.g. {@code Lobby-1}.
     */
    @Getter
    private String serverName;
    private UUID serverKey;
    private int wrapperPort;
    /**
     * Internal: the secret this run proves itself with to the wrapper.
     */
    @Getter
    private String wrapperToken;

    @Override
    public void onEnable() {
        instance = this;
        var load = JsonConfiguration.load(Path.of("cloud.json"), JsonObject.class);
        if (load == null) {
            getLogger().severe("No cloud.json found");
            getServer().shutdown();
            return;
        }
        serverName = load.get("ServerName").getAsString();
        serverKey = UUID.fromString(load.get("ServerKey").getAsString());
        wrapperToken = load.has("WrapperToken") ? load.get("WrapperToken").getAsString() : "";
        wrapperPort = load.has("WrapperPort") ? load.get("WrapperPort").getAsInt() : ServerClient.DEFAULT_PORT;
        wrapperReconnect = new WrapperReconnect(this::initServerClient,
                () -> Bukkit.getScheduler().runTask(this, () -> Bukkit.getServer().shutdown()), getLogger()::warning);
        var pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new PlayerJoinListener(), this);
        initServerClient();
    }

    /**
     * Sends the players on this server to a fallback server and shuts the server down.
     */
    public void shutdownServer() {
        RexlCloudApi.getInstance().getCachedPlayers().stream()
                .filter(player -> player.getServer().getName().equals(RexlCloudApi.getInstance().getServerName()))
                .forEach(de.rexlcloud.core.api.Player::connectToFallbackServer);
        Bukkit.getServer().shutdown();
    }

    @Override
    public void onDisable() {
        if (wrapperReconnect != null) {
            wrapperReconnect.stop();
        }
        if (RexlCloudApi.getInstance() == null || serverClient == null) {
            // onEnable did not get far, nothing to clean up
            return;
        }
        RexlCloudApi.getInstance().getCachedPlayers().stream()
                .filter(player -> player.getServer().getName().equals(getServerName()))
                .forEach(Player::connectToFallbackServer);
        if (serverClient.getChannel() != null && serverClient.getChannel().isActive() && serverClient.getChannel().isOpen()) {
            serverClient.getChannel().writeAndFlush(new PacketContainer(PacketType.SERVER_STOP, new ServerStopPacket(serverName, serverKey)));
        }
    }

    /**
     * Internal: opens the connection to the wrapper.
     */
    public void initServerClient() {
        getLogger().info("STARTING SERVER_CLIENT");
        serverClient = new ServerClient(wrapperPort);
        new Thread(serverClient).start();
    }
}
