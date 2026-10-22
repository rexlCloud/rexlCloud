package de.rexlcloud.proxy.bungee.networking;

import de.rexlcloud.proxy.bungee.BungeeText;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.config.PluginMessageConfig;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.SerialExecutor;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerOnlinePacket;
import de.rexlcloud.core.network.packets.*;
import de.rexlcloud.proxy.bungee.BungeeProxy;
import de.rexlcloud.proxy.common.config.CommonSettings;
import de.rexlcloud.proxy.common.config.PluginMessages;
import de.rexlcloud.proxy.common.messages.MessageParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;

import static de.rexlcloud.core.config.MessageKey.PLAYER_NOTIFY_SERVER_OFFLINE;
import static de.rexlcloud.core.config.MessageKey.PLAYER_NOTIFY_SERVER_ONLINE;

public class ServerHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * Handles this connection's packets in order, without a new thread per packet.
     */
    private final SerialExecutor packetQueue = new SerialExecutor();

    private boolean isStopping = false;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        packetQueue.execute(() -> {
            try {
                var packetContainer = (PacketContainer) msg;
                var proxy = BungeeProxy.getInstance();
                switch (packetContainer.getPacketType()) {
                    case SERVER_STOP -> {
                        proxy.getLogger().info("Server got stopped from Wrapper");
                        isStopping = true;
                        BungeeProxy.getInstance().shutdownServer();
                    }
                    case PROXY_ADD_SERVER -> {
                        var serverAddPacket = (ProxyAddServerPacket) packetContainer.getPacket();
                        var address = new InetSocketAddress(serverAddPacket.getAddress(), serverAddPacket.getPort());
                        var known = proxy.getServer().getServers().get(serverAddPacket.getName());
                        if (known != null && address.equals(known.getSocketAddress())) {
                            // Announced again, e.g. after the master took the running servers over
                            break;
                        }
                        proxy.getLogger().info("Registered Server: " + serverAddPacket.getName());
                        var serverInfo = proxy.getServer().constructServerInfo(serverAddPacket.getName(), address,
                                serverAddPacket.getName(), false);

                        proxy.getServer().getServers().put(serverAddPacket.getName(), serverInfo);
                        if (serverAddPacket.isFallback())
                            proxy.getLogin().addFallbackServer(serverInfo);
                        proxy.getServer().getPlayers().stream()
                                .filter(player -> player.hasPermission("rexlcloud.notify.server.online"))
                                .forEach(player -> BungeeText.send(player, PluginMessages.getInstance().getMessage(PLAYER_NOTIFY_SERVER_ONLINE,
                                                Placeholder.parsed("server", serverAddPacket.getName()))));
                    }
                    case PLAYER_SYNC_REQUEST -> proxy.getServer().getPlayers().stream()
                            .filter(player -> player.getServer() != null)
                            .forEach(player -> proxy.writePacket(new PacketContainer(PacketType.PLAYER_JOIN,
                                    new PlayerCloudJoinPacket(player.getName(), player.getUniqueId(),
                                            System.currentTimeMillis(), player.getServer().getInfo().getName(),
                                            proxy.getServerName()))));
                    case PROXY_REMOVE_SERVER -> {
                        var proxyRemoveServerPacket = (ProxyRemoveServerPacket) packetContainer.getPacket();
                        proxy.getLogger().info("Unregistered Server: " + proxyRemoveServerPacket.getName());
                        var serverInfo = proxy.getServer().getServerInfo(proxyRemoveServerPacket.getName());
                        if (serverInfo != null) {
                            proxy.getServer().getServers().remove(serverInfo.getName());
                            if (proxy.getLogin().getFallbackServers().contains(serverInfo)) {
                                proxy.getLogin().removeFallbackServer(serverInfo);
                            }
                            proxy.getServer().getPlayers().stream()
                                    .filter(player -> player.hasPermission("rexlcloud.notify.server.offline"))
                                    .forEach(player -> BungeeText.send(player, PluginMessages.getInstance().getMessage(PLAYER_NOTIFY_SERVER_OFFLINE,
                                                    Placeholder.parsed("server", proxy.getServerName()))));
                        }
                    }
                    case PLAYER_KICK -> {
                        var playerKickPacket = (PlayerKickPacket) packetContainer.getPacket();
                        var serialize = BungeeText.of(MessageParser.parse(playerKickPacket.getMessage()));
                        var player = proxy.getServer().getPlayer(playerKickPacket.getUuid());
                        if (player != null) {
                            player.disconnect(serialize);
                        }
                    }
                    case PLAYER_SEND_MESSAGE -> {
                        var playerMessagePacket = (PlayerMessagePacket) packetContainer.getPacket();
                        var player = proxy.getServer().getPlayer(playerMessagePacket.getUuid());
                        if (player != null) {
                            BungeeText.send(player, GsonComponentSerializer.gson().deserialize(playerMessagePacket.getMessage()));
                        }
                    }
                    case PLAYER_CHANGE_SERVER -> {
                        var playerMessagePacket = (PlayerChangeServerPacket) packetContainer.getPacket();
                        var player = proxy.getServer().getPlayer(playerMessagePacket.getUuid());
                        if (player != null) {
                            var serverInfo = proxy.getServer().getServerInfo(playerMessagePacket.getServerName());
                            if (serverInfo != null) {
                                player.connect(serverInfo, ServerConnectEvent.Reason.PLUGIN);
                            }
                        }
                    }
                    case SYNC_MESSAGES ->
                            PluginMessages.getInstance().updateMessages((PluginMessageConfig) packetContainer.getPacket());
                    case MAINTENANCE_CHANGE ->
                            CommonSettings.getInstance().setMaintenance(((MaintenanceModeChangePacket) packetContainer.getPacket()).value());
                }
                BungeeProxy.getInstance().getRexlCloudApi().receivePacket(packetContainer);
            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                ReferenceCountUtil.release(msg);
            }

        });
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) {
        var serverName = BungeeProxy.getInstance().getServerName();
        BungeeProxy.getInstance().getWrapperReconnect().connected();
        BungeeProxy.getInstance().setRexlCloudApi(RexlCloudApi.connect(ctx.channel(), 100, serverName));
        var container = new PacketContainer(PacketType.SERVER_ONLINE,
                new ServerOnlinePacket(serverName, BungeeProxy.getInstance().getWrapperToken()));
        ctx.channel().writeAndFlush(container);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        // The wrapper went away (e.g. it restarts while its servers keep running): connect again for a while
        if (!isStopping) {
            BungeeProxy.getInstance().getWrapperReconnect().lost();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            // channelUnregistered follows
            ctx.close();
            return;
        }
        cause.printStackTrace();
    }
}
