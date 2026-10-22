package de.rexlcloud.proxy.velocity;

import de.rexlcloud.core.network.WrapperReconnect;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.velocity.CloudInjectionModule;
import org.incendo.cloud.velocity.VelocityCommandManager;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.proxy.common.config.CommonSettings;
import de.rexlcloud.proxy.velocity.commands.*;
import de.rexlcloud.proxy.velocity.listener.Login;
import de.rexlcloud.proxy.velocity.networking.ServerClient;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.UUID;

/**
 * The rexlCloud plugin for Velocity proxies, installed by the cloud on every proxy. It registers the cloud's
 * servers with Velocity, sends joining players to a fallback server, shows the MOTD from the cloud and adds the
 * {@code /cloud} commands.
 * <p>
 * Other plugins use the cloud through {@link RexlCloudApi#getInstance()} (or {@link #getRexlCloudApi()}); declare a
 * dependency on {@code rexlcloud_proxy} so it is initialized first.
 */
@Plugin(id = "rexlcloud_proxy", name = "rexlCloudProxy", version = "1.0.0", description = "Proxy for rexlCloud", authors = {"rexlNico"})
public class VelocityProxy {

    //TODO: cloud command to save temp to template

    /**
     * The plugin instance.
     */
    @Getter
    private static VelocityProxy instance;
    private ServerClient serverClient;
    /**
     * Internal: connects again when the connection to the wrapper breaks.
     */
    @Getter
    private WrapperReconnect wrapperReconnect;
    /**
     * The Velocity proxy.
     */
    @Getter
    private ProxyServer server;
    /**
     * The plugin's logger.
     */
    @Getter
    private Logger logger;
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
    private RexlCloudApi rexlCloudApi;
    @Inject
    private Injector injector;

    /**
     * The command manager of the {@code /cloud} commands.
     */
    @Getter
    private VelocityCommandManager<CommandSource> commandManager;

    /**
     * Internal: created by Velocity.
     */
    @Inject
    public VelocityProxy(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    /**
     * Internal: starts the plugin.
     */
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;
        this.login = new Login();
        server.getEventManager().register(this, this.login);
        var load = JsonConfiguration.load(Path.of("cloud.json"), JsonObject.class);
        if (load == null) {
            getLogger().error("No cloud.json found");
            getServer().shutdown();
            return;
        }
        serverName = load.get("ServerName").getAsString();
        serverKey = UUID.fromString(load.get("ServerKey").getAsString());
        wrapperToken = load.has("WrapperToken") ? load.get("WrapperToken").getAsString() : "";
        wrapperPort = load.has("WrapperPort") ? load.get("WrapperPort").getAsInt() : ServerClient.DEFAULT_PORT;
        CommonSettings.getInstance().setMaintenance(load.get("Maintenance").getAsBoolean());

        injector = this.injector.createChildInjector(
                new CloudInjectionModule<>(
                        CommandSource.class,
                        ExecutionCoordinator.simpleCoordinator(),
                        SenderMapper.identity()
                )
        );
        commandManager = injector.getInstance(
                Key.get(new TypeLiteral<VelocityCommandManager<CommandSource>>() {
                })
        );
        var annotationParser = new AnnotationParser<>(commandManager, CommandSource.class);
        annotationParser.parse(new CloudCommandsGroup());
        annotationParser.parse(new CloudCommandsPlayer());
        annotationParser.parse(new CloudCommandsServer());
        annotationParser.parse(new CloudCommandsWrapper());
        annotationParser.parse(new LobbyCommands());

        var registeredServers = getInstance().getServer().getAllServers().stream().toList();
        registeredServers.forEach(server1 -> getServer().unregisterServer(server1.getServerInfo()));

        wrapperReconnect = new WrapperReconnect(this::initServerClient, () -> server.shutdown(), logger::warn);
        initServerClient();
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
     * Internal: tells the wrapper the proxy is stopping.
     */
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (wrapperReconnect != null) {
            wrapperReconnect.stop();
        }
        if (serverClient.getChannel() != null && serverClient.getChannel().isActive() && serverClient.getChannel().isOpen()) {
            serverClient.getChannel().writeAndFlush(new PacketContainer(PacketType.SERVER_STOP, new ServerStopPacket(serverName, serverKey)));
        }
        server.getAllPlayers().forEach(player -> {
            player.disconnect(Component.text("The server is stopping!"));
        });
    }

    /**
     * Internal: opens the connection to the wrapper.
     */
    public void initServerClient() {
        logger.info("STARTING SERVER_CLIENT");
        serverClient = new ServerClient(wrapperPort);
        new Thread(serverClient).start();
    }

    /**
     * Disconnects all players and shuts the proxy down.
     */
    public void shutdownServer() {
        server.getAllPlayers().forEach(player -> {
            player.disconnect(Component.text("The server is stopping!"));
        });
        server.shutdown();
    }

}
