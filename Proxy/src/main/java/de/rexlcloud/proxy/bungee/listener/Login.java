package de.rexlcloud.proxy.bungee.listener;

import de.rexlcloud.proxy.bungee.BungeeText;
import de.rexlcloud.core.config.MessageKey;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerCloudJoinPacket;
import de.rexlcloud.core.network.packets.PlayerProxyDisconnectPacket;
import de.rexlcloud.core.network.packets.PlayerServerChangePacket;
import de.rexlcloud.proxy.bungee.BungeeProxy;
import de.rexlcloud.proxy.common.config.CommonSettings;
import de.rexlcloud.proxy.common.config.PluginMessages;
import lombok.Getter;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

public class Login implements Listener {

    @Getter
    private final ArrayList<ServerInfo> fallbackServers = new ArrayList<>();

    public void addFallbackServer(ServerInfo server) {
        if (!fallbackServers.contains(server)) {
            fallbackServers.add(server);
        }
    }

    public void removeFallbackServer(ServerInfo server) {
        fallbackServers.remove(server);
    }

    public Optional<ServerInfo> getFallbackServer() {
        return fallbackServers.stream().min(Comparator.comparingLong(value ->
                BungeeProxy.getInstance().getServer().getPlayers().stream().filter(player -> player.getServer().getInfo().equals(value)).count()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(ProxyPingEvent event) {
        // MiniMessage MOTD from the cloud (can be changed in the web panel)
        PluginMessages.getInstance().getMotd().ifPresent(motd ->
                event.getResponse().setDescriptionComponent(new TextComponent(
                        BungeeText.of(motd))));
        if (BungeeProxy.getInstance().getRexlCloudApi() != null) {
            BungeeProxy.getInstance().getRexlCloudApi().getCachedServer(BungeeProxy.getInstance().getServerName()).ifPresent(server -> {
                event.getResponse().getPlayers().setMax(server.getServerGroup().getMaxPlayers());
            });
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChoseInitialServer(ServerConnectEvent event) {
        if (event.getReason() == ServerConnectEvent.Reason.LOBBY_FALLBACK || event.getReason() == ServerConnectEvent.Reason.KICK_REDIRECT
                || event.getReason() == ServerConnectEvent.Reason.JOIN_PROXY || event.getReason() == ServerConnectEvent.Reason.SERVER_DOWN_REDIRECT) {
            if (CommonSettings.getInstance().isMaintenance() && !event.getPlayer().hasPermission("rexlcloud.maintenance.join")) {
                var serialize = BungeeText.of(PluginMessages.getInstance().getMessage(MessageKey.KICK_SERVER_MAINTENANCE));
                event.getPlayer().disconnect(serialize);
                return;
            }
            getFallbackServer().ifPresentOrElse(event::setTarget, () -> {
                var serialize = BungeeText.of(PluginMessages.getInstance().getMessage(MessageKey.KICK_NO_SERVER_FOUND));
                event.getPlayer().disconnect(serialize);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerDisconnect(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();

        var playerCloudJoinPacket = new PlayerCloudJoinPacket(
                player.getName(),
                player.getUniqueId(),
                System.currentTimeMillis(),
                event.getPlayer().getServer().getInfo().getName(),
                BungeeProxy.getInstance().getServerName());
        var packetContainer = new PacketContainer(PacketType.PLAYER_JOIN, playerCloudJoinPacket);
        BungeeProxy.getInstance().writePacket(packetContainer);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        var playerProxyDisconnectPacket = new PlayerProxyDisconnectPacket(player.getUniqueId());
        var packetContainer = new PacketContainer(PacketType.PLAYER_QUIT, playerProxyDisconnectPacket);
        BungeeProxy.getInstance().writePacket(packetContainer);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerDisconnect(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (event.getFrom() == null) {
            return;
        }
        var playerServerChangePacket = new PlayerServerChangePacket(player.getUniqueId(), player.getServer().getInfo().getName());
        var packetContainer = new PacketContainer(PacketType.PLAYER_SERVER_CHANGE, playerServerChangePacket);
        BungeeProxy.getInstance().writePacket(packetContainer);
    }

}
