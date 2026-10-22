package de.rexlcloud.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.rexlcloud.bridge.helper.RexlCloudHelper;
import de.rexlcloud.bridge.networking.ServerClient;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.network.WrapperReconnect;
import de.rexlcloud.core.network.packets.PlayerTeleportPacket;
import lombok.Getter;
import lombok.Setter;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.player.PlayerLoadedEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Connects a Minestom server that rexlCloud started from a "Custom jar" group to its wrapper. In the server's main:
 *
 * <pre>{@code
 * var cloud = RexlCloudBridge.getInstance();
 * var server = MinecraftServer.init(cloud.getAuth());
 * // ... instances, events ...
 * cloud.initialize();
 * server.start("0.0.0.0", cloud.getPort());
 * }</pre>
 * <p>
 * Everything comes from the {@code cloud.json} the wrapper writes into the server's folder before each start. Outside
 * the cloud (no cloud.json), {@link #isCloudServer()} is {@code false}, {@link #getPort()} returns 25565 and
 * {@link #initialize()} does nothing, so the same jar can be started locally for testing.
 */
public final class RexlCloudBridge {

    private static final int DEFAULT_SERVER_PORT = 25565;
    private static final int DEFAULT_WRAPPER_PORT = 2243;

    @Getter
    private static final RexlCloudBridge instance = new RexlCloudBridge(Path.of("cloud.json"));

    @Nullable
    private final JsonObject cloudInfo;
    @Getter
    @Setter
    private volatile RexlCloudApi rexlCloudApi;
    /**
     * Teleports for players that are still joining, done once they are loaded.
     */
    @Getter
    private final List<PlayerTeleportPacket> bufferedPlayerTeleports = new CopyOnWriteArrayList<>();
    private Runnable onShutdown = () -> {
        MinecraftServer.stopCleanly();
        System.exit(0);
    };
    private boolean initialized;
    @Getter
    private final WrapperReconnect wrapperReconnect = new WrapperReconnect(
            () -> ServerClient.start(getWrapperPort()), this::shutdownServer,
            message -> System.out.println("[rexlCloud] " + message));

    RexlCloudBridge(Path cloudJson) {
        JsonObject info = null;
        if (Files.isRegularFile(cloudJson)) {
            try {
                info = JsonParser.parseString(Files.readString(cloudJson)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                System.err.println("[rexlCloud] cloud.json could not be read: " + e.getMessage());
            }
        }
        this.cloudInfo = info;
    }

    /**
     * Whether the server was started by rexlCloud.
     */
    public boolean isCloudServer() {
        return cloudInfo != null;
    }

    @Nullable
    public String getServerName() {
        return string("ServerName", null);
    }

    @Nullable
    public UUID getServerKey() {
        var key = string("ServerKey", null);
        return key == null ? null : UUID.fromString(key);
    }

    /**
     * The port the server has to listen on.
     */
    public int getPort() {
        return cloudInfo != null && cloudInfo.has("ServerPort") ? cloudInfo.get("ServerPort").getAsInt() : DEFAULT_SERVER_PORT;
    }

    /**
     * Which template variant (e.g. map) the server got, "default" if its group has none.
     */
    public String getTemplateVariant() {
        return string("TemplateVariant", "default");
    }

    public boolean isMaintenance() {
        return cloudInfo != null && cloudInfo.has("Maintenance") && cloudInfo.get("Maintenance").getAsBoolean();
    }

    /**
     * How players reach the server, matching the cloud's proxies: Velocity modern forwarding with the network's
     * secret, BungeeCord forwarding, or offline mode outside the cloud. Pass it to {@code MinecraftServer.init}.
     */
    public Auth getAuth() {
        var mode = string("ForwardingMode", "");
        var secret = string("ForwardingSecret", "");
        if (mode.equals("MODERN") && !secret.isEmpty()) {
            return new Auth.Velocity(secret);
        }
        if (mode.equals("LEGACY")) {
            return new Auth.Bungee();
        }
        return new Auth.Offline();
    }

    public int getWrapperPort() {
        return cloudInfo != null && cloudInfo.has("WrapperPort") ? cloudInfo.get("WrapperPort").getAsInt() : DEFAULT_WRAPPER_PORT;
    }

    /**
     * The secret this run proves itself with to the wrapper.
     */
    public String getWrapperToken() {
        return string("WrapperToken", "");
    }

    /**
     * Connects to the wrapper and handles the cloud's player teleports. Call it once, after
     * {@code MinecraftServer.init}. Without a cloud.json it does nothing.
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!isCloudServer()) {
            System.out.println("[rexlCloud] No cloud.json found, running without the cloud");
            return;
        }
        MinecraftServer.getGlobalEventHandler().addListener(PlayerLoadedEvent.class, event -> {
            var player = event.getPlayer();
            for (var teleport : bufferedPlayerTeleports) {
                if (!teleport.getUuid().equals(player.getUuid())) {
                    continue;
                }
                bufferedPlayerTeleports.remove(teleport);
                if (teleport.getBufferTime() >= System.currentTimeMillis()) {
                    RexlCloudHelper.teleport(player, teleport.getServerLocation());
                }
            }
        });
        ServerClient.start(getWrapperPort());
    }

    /**
     * What happens when the cloud stops the server or the wrapper goes away. By default the server stops and the
     * process exits.
     */
    public void setShutdownHook(Runnable onShutdown) {
        this.onShutdown = onShutdown;
    }

    public void shutdownServer() {
        wrapperReconnect.stop();
        if (onShutdown != null) {
            onShutdown.run();
        }
    }

    private String string(String key, String fallback) {
        return cloudInfo != null && cloudInfo.has(key) && !cloudInfo.get(key).isJsonNull()
                ? cloudInfo.get(key).getAsString() : fallback;
    }

}
