package de.rexlcloud.proxy.velocity.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.rexlcloud.core.config.MessageKey;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerCloudJoinPacket;
import de.rexlcloud.core.network.packets.PlayerProxyDisconnectPacket;
import de.rexlcloud.core.network.packets.PlayerServerChangePacket;
import de.rexlcloud.proxy.common.config.CommonSettings;
import de.rexlcloud.proxy.common.config.PluginMessages;
import de.rexlcloud.proxy.velocity.VelocityProxy;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

public class Login {

    @Getter
    private final ArrayList<RegisteredServer> fallbackServers = new ArrayList<>();

    public void addFallbackServer(RegisteredServer server) {
        if (!fallbackServers.contains(server)) {
            fallbackServers.add(server);
        }
    }

    public void removeFallbackServer(RegisteredServer server) {
        fallbackServers.remove(server);
    }

    public Optional<RegisteredServer> getFallbackServer() {
        return fallbackServers.stream().min(Comparator.comparingInt(value -> value.getPlayersConnected().size()));
    }

    /*@Subscribe(order = PostOrder.FIRST)
    public void onServerKick(KickedFromServerEvent event) {
        getFallbackServer().ifPresentOrElse(server -> event.setResult(KickedFromServerEvent.RedirectPlayer.create(server)),
                () -> event.setResult(KickedFromServerEvent.DisconnectPlayer
                        .create(PluginMessages.getInstance().getMessage(MessageKey.KICK_NO_SERVER_FOUND))));
    }*/

    @Subscribe(order = PostOrder.FIRST)
    public void onChoseInitialServer(PlayerChooseInitialServerEvent event) {
        if (CommonSettings.getInstance().isMaintenance() && !event.getPlayer().hasPermission("rexlcloud.maintenance.join")) {
            event.getPlayer().disconnect(PluginMessages.getInstance().getMessage(MessageKey.KICK_SERVER_MAINTENANCE));
            return;
        }
        getFallbackServer().ifPresentOrElse(server -> {
            event.setInitialServer(server);
            var player = event.getPlayer();
            var playerCloudJoinPacket = new PlayerCloudJoinPacket(
                    player.getUsername(),
                    player.getUniqueId(),
                    System.currentTimeMillis(),
                    server.getServerInfo().getName(),
                    VelocityProxy.getInstance().getServerName());
            var packetContainer = new PacketContainer(PacketType.PLAYER_JOIN, playerCloudJoinPacket);
            VelocityProxy.getInstance().writePacket(packetContainer);
        }, () ->
                event.getPlayer().disconnect(PluginMessages.getInstance().getMessage(MessageKey.KICK_NO_SERVER_FOUND)));
    }

    @Subscribe(order = PostOrder.FIRST)
    public void on(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(server -> {
            var playerServerChangePacket = new PlayerServerChangePacket(player.getUniqueId(), server.getServerInfo().getName());
            var packetContainer = new PacketContainer(PacketType.PLAYER_SERVER_CHANGE, playerServerChangePacket);
            VelocityProxy.getInstance().writePacket(packetContainer);
        });
    }

    @Subscribe(order = PostOrder.FIRST)
    public void on(ProxyPingEvent event) {
        // MiniMessage MOTD from the cloud (can be changed in the web panel)
        PluginMessages.getInstance().getMotd().ifPresent(motd ->
                event.setPing(event.getPing().asBuilder().description(motd).build()));
        if (VelocityProxy.getInstance().getRexlCloudApi() != null) {
            VelocityProxy.getInstance().getRexlCloudApi().getCachedServer(VelocityProxy.getInstance().getServerName()).ifPresent(server -> {
                var ping = event.getPing().asBuilder().maximumPlayers(server.getServerGroup().getMaxPlayers()).build();
                event.setPing(ping);
            });
        }
    }

    @Subscribe(order = PostOrder.FIRST)
    public void on(DisconnectEvent event) {
        Player player = event.getPlayer();
        var playerProxyDisconnectPacket = new PlayerProxyDisconnectPacket(player.getUniqueId());
        var packetContainer = new PacketContainer(PacketType.PLAYER_QUIT, playerProxyDisconnectPacket);
        VelocityProxy.getInstance().writePacket(packetContainer);
    }

}
