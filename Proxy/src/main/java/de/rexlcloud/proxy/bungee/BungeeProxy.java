package de.rexlcloud.proxy.bungee;

import de.rexlcloud.core.network.WrapperReconnect;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.bungee.BungeeCommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import com.google.gson.JsonObject;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.proxy.bungee.commands.*;
import de.rexlcloud.proxy.bungee.listener.Login;
import de.rexlcloud.proxy.bungee.networking.ServerClient;
import de.rexlcloud.proxy.common.config.CommonSettings;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The rexlCloud plugin for BungeeCord proxies, installed by the cloud on every proxy. It registers the cloud's
 * servers with BungeeCord, sends joining players to a fallback server, shows the MOTD from the cloud and adds the
 * {@code /cloud} commands. BungeeCord only supports legacy forwarding ({@code forwardingMode} LEGACY on the master).
 * <p>
 * Other plugins use the cloud through {@link RexlCloudApi#getInstance()} (or {@link #getRexlCloudApi()}); add
 * {@code depends: [rexlCloudProxy]} to their bungee.yml.
 */
public class BungeeProxy extends Plugin {

    @Getter
    private static BungeeProxy instance;
    @Getter
    private final List<Callable<Void>> shutdownHooks = new ArrayList<>();
    private ServerClient serverClient;
    /**
     * Internal: connects again when the connection to the wrapper breaks.
     */
    @Getter
    private WrapperReconnect wrapperReconnect;

    /**
     * The BungeeCord proxy.
     */
    @Getter
    private ProxyServer server;

    /**
     * Name of this proxy in the cloud, e.g. {@code Proxy-1}.
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
    /**
     * Internal: the fallback servers players are sent to.
     */
    @Getter
    private Login login;
    /**
     * The cloud API, {@code null} until the first connection to the wrapper.
     */
    @Getter
    @Setter
    RexlCloudApi rexlCloudApi;
    /**
     * The command manager of the {@code /cloud} commands.
     */
    @Getter
    private BungeeCommandManager<CommandSender> commandManager;


    /**
     * Internal: starts the plugin.
     */
    @Override
    public void onEnable() {
        instance = this;

        try {
            commandManager = new BungeeCommandManager<>(this, ExecutionCoordinator.asyncCoordinator(), SenderMapper.identity());
        } catch (final Exception e) {
            this.getLogger().severe("Failed to initialize the command manager");
            return;
        }

        var annotationParser = new AnnotationParser<>(commandManager, CommandSender.class);
        annotationParser.parse(new CloudCommandsGroup());
        annotationParser.parse(new CloudCommandsPlayer());
        annotationParser.parse(new CloudCommandsServer());
        annotationParser.parse(new CloudCommandsWrapper());
        annotationParser.parse(new LobbyCommands());

        this.login = new Login();
        ProxyServer.getInstance().getPluginManager().registerListener(this, login);
        server = ProxyServer.getInstance();
        var load = JsonConfiguration.load(Path.of("cloud.json"), JsonObject.class);
        if (load == null) {
            getLogger().severe("No cloud.json found");
            getServer().stop();
            return;
        }
        serverName = load.get("ServerName").getAsString();
        serverKey = UUID.fromString(load.get("ServerKey").getAsString());
        wrapperToken = load.has("WrapperToken") ? load.get("WrapperToken").getAsString() : "";
        wrapperPort = load.has("WrapperPort") ? load.get("WrapperPort").getAsInt() : ServerClient.DEFAULT_PORT;
        CommonSettings.getInstance().setMaintenance(load.get("Maintenance").getAsBoolean());
        wrapperReconnect = new WrapperReconnect(this::initServerClient, () -> getProxy().stop(), getLogger()::warning);
        initServerClient();
    }

    /**
     * Internal: tells the wrapper the proxy is stopping.
     */
    @Override
    public void onDisable() {
        if (wrapperReconnect != null) {
            wrapperReconnect.stop();
        }
        if (serverClient.getChannel() != null && serverClient.getChannel().isActive() && serverClient.getChannel().isOpen()) {
            serverClient.getChannel().writeAndFlush(new PacketContainer(PacketType.SERVER_STOP, new ServerStopPacket(serverName, serverKey)));
        }
    }

    /**
     * Internal: sends a packet to the wrapper.
     */
    public void writePacket(PacketContainer packetContainer) {
        if (serverClient.getChannel() != null && serverClient.getChannel().isActive() && serverClient.getChannel().isOpen()) {
            serverClient.getChannel().writeAndFlush(packetContainer);
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

    /**
     * Runs the task (at most 30 seconds) before the cloud shuts the proxy down.
     */
    public void registerShutdownHook(Callable<Void> callable) {
        shutdownHooks.add(callable);
    }

    /**
     * Removes a task added with {@link #registerShutdownHook(Callable)}.
     */
    public void unregisterShutdownHook(Callable<Void> callable) {
        shutdownHooks.remove(callable);
    }

    /**
     * Runs the shutdown hooks, disconnects all players and shuts the proxy down.
     */
    public void shutdownServer() {
        var executorService = Executors.newFixedThreadPool(shutdownHooks.size());
        try {
            var futures = executorService.invokeAll(shutdownHooks);
            var timeout = 30000L;
            var timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
            try {
                timeoutExecutor.schedule(() -> {
                    for (var future : futures) {
                        if (!future.isDone()) {
                            future.cancel(true);
                        }
                    }
                }, timeout, TimeUnit.MILLISECONDS);
                for (var future : futures) {
                    future.get();
                }
                getServer().getPlayers().forEach(player -> {
                    player.disconnect("The server is stopping!");
                });
                getServer().stop();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                timeoutExecutor.shutdown();
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
            getServer().stop();
        } finally {
            executorService.shutdown();
        }
    }

}
