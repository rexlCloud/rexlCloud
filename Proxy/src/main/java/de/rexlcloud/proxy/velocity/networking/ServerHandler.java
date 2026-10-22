package de.rexlcloud.proxy.velocity.networking;

import com.velocitypowered.api.proxy.server.ServerInfo;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.config.PluginMessageConfig;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.SerialExecutor;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerOnlinePacket;
import de.rexlcloud.core.network.packets.*;
import de.rexlcloud.proxy.common.config.CommonSettings;
import de.rexlcloud.proxy.common.config.PluginMessages;
import de.rexlcloud.proxy.common.messages.MessageParser;
import de.rexlcloud.proxy.velocity.VelocityProxy;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
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
                var proxy = VelocityProxy.getInstance();
                switch (packetContainer.getPacketType()) {
                    case SERVER_STOP -> {
                        proxy.getLogger().info("Server got stopped from Wrapper");
                        isStopping = true;
                        VelocityProxy.getInstance().shutdownServer();
                    }
                    case PROXY_ADD_SERVER -> {
                        var serverAddPacket = (ProxyAddServerPacket) packetContainer.getPacket();
                        var inetSocketAddress = new InetSocketAddress(serverAddPacket.getAddress(), serverAddPacket.getPort());
                        var known = proxy.getServer().getServer(serverAddPacket.getName());
                        if (known.isPresent() && known.get().getServerInfo().getAddress().equals(inetSocketAddress)) {
                            // Announced again, e.g. after the master took the running servers over
                            break;
                        }
                        proxy.getLogger().info("Registered Server: " + serverAddPacket.getName());
                        var registeredServer = proxy.getServer().registerServer(new ServerInfo(serverAddPacket.getName(), inetSocketAddress));
                        if (serverAddPacket.isFallback())
                            proxy.getLogin().addFallbackServer(registeredServer);
                        proxy.getServer().getAllPlayers().stream()
                                .filter(player -> player.hasPermission("rexlcloud.notify.server.online"))
                                .forEach(player -> player.sendMessage(PluginMessages.getInstance().getMessage(PLAYER_NOTIFY_SERVER_ONLINE,
                                        Placeholder.parsed("server", registeredServer.getServerInfo().getName()))));
                    }
                    case PLAYER_SYNC_REQUEST -> proxy.getServer().getAllPlayers().forEach(player ->
                            player.getCurrentServer().ifPresent(server -> proxy.writePacket(new PacketContainer(
                                    PacketType.PLAYER_JOIN, new PlayerCloudJoinPacket(player.getUsername(),
                                    player.getUniqueId(), System.currentTimeMillis(),
                                    server.getServerInfo().getName(), proxy.getServerName())))));
                    case PROXY_REMOVE_SERVER -> {
                        var proxyRemoveServerPacket = (ProxyRemoveServerPacket) packetContainer.getPacket();
                        proxy.getLogger().info("Unregistered Server: " + proxyRemoveServerPacket.getName());
                        proxy.getServer().getServer(proxyRemoveServerPacket.getName()).ifPresent(registeredServer -> {
                            proxy.getServer().unregisterServer(registeredServer.getServerInfo());
                            if (proxy.getLogin().getFallbackServers().contains(registeredServer)) {
                                proxy.getLogin().removeFallbackServer(registeredServer);
                            }
                            proxy.getServer().getAllPlayers().stream()
                                    .filter(player -> player.hasPermission("rexlcloud.notify.server.offline"))
                                    .forEach(player -> player.sendMessage(PluginMessages.getInstance().getMessage(PLAYER_NOTIFY_SERVER_OFFLINE,
                                            Placeholder.parsed("server", registeredServer.getServerInfo().getName()))));
                        });
                    }
                    case PLAYER_KICK -> {
                        var playerKickPacket = (PlayerKickPacket) packetContainer.getPacket();
                        proxy.getServer().getPlayer(playerKickPacket.getUuid()).ifPresent(player -> {
                            player.disconnect(MessageParser.parse(playerKickPacket.getMessage()));
                        });
                    }
                    case PLAYER_SEND_MESSAGE -> {
                        var playerMessagePacket = (PlayerMessagePacket) packetContainer.getPacket();
                        proxy.getServer().getPlayer(playerMessagePacket.getUuid()).ifPresent(player -> {
                            player.sendMessage(GsonComponentSerializer.gson().deserialize(playerMessagePacket.getMessage()));
                        });
                    }
                    case PLAYER_CHANGE_SERVER -> {
                        var playerMessagePacket = (PlayerChangeServerPacket) packetContainer.getPacket();
                        proxy.getServer().getPlayer(playerMessagePacket.getUuid()).ifPresent(player -> {
                            proxy.getServer().getServer(playerMessagePacket.getServerName()).ifPresent(server -> {
                                player.createConnectionRequest(server).connect();
                            });
                        });
                    }
                    case SYNC_MESSAGES ->
                            PluginMessages.getInstance().updateMessages((PluginMessageConfig) packetContainer.getPacket());
                    case MAINTENANCE_CHANGE ->
                            CommonSettings.getInstance().setMaintenance(((MaintenanceModeChangePacket) packetContainer.getPacket()).value());
                }
                proxy.getRexlCloudApi().receivePacket(packetContainer);
            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                ReferenceCountUtil.release(msg);
            }

        });
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) {
        var serverName = VelocityProxy.getInstance().getServerName();
        VelocityProxy.getInstance().getWrapperReconnect().connected();
        VelocityProxy.getInstance().setRexlCloudApi(RexlCloudApi.connect(ctx.channel(), 100, serverName));

        var container = new PacketContainer(PacketType.SERVER_ONLINE,
                new ServerOnlinePacket(serverName, VelocityProxy.getInstance().getWrapperToken()));
        ctx.channel().writeAndFlush(container);

    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        // The wrapper went away (e.g. it restarts while its servers keep running): connect again for a while
        if (!isStopping) {
            VelocityProxy.getInstance().getWrapperReconnect().lost();
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
