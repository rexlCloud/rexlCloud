package de.rexlcloud.bridge.networking;

import de.rexlcloud.bridge.Bridge;
import de.rexlcloud.bridge.helper.RexlCloudHelper;
import de.rexlcloud.bridge.listener.PlayerJoinListener;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.SerialExecutor;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerOnlinePacket;
import de.rexlcloud.core.network.packets.PlayerTeleportPacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacketToSpecific;
import de.rexlcloud.core.network.packets.api.ApiResponsePacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.UUID;

public class ServerHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * Handles this connection's packets in order, without a new thread per packet.
     */
    private final SerialExecutor packetQueue = new SerialExecutor();

    private boolean isStopping = false;

    @Override
    protected final void channelRead0(ChannelHandlerContext ctx, Object msg) {
        packetQueue.execute(() -> {
            try {
                var packetContainer = (PacketContainer) msg;
                var bridge = Bridge.getInstance();
                switch (packetContainer.getPacketType()) {
                    case SERVER_STOP -> {
                        bridge.getLogger().info("Server got stopped from Wrapper");
                        isStopping = true;
                        Bridge.getInstance().shutdownServer();
                    }
                    case PLAYER_TELEPORT -> {
                        var packet = (PlayerTeleportPacket) packetContainer.getPacket();
                        var player = Bukkit.getPlayer(packet.getUuid());
                        if (player != null) {
                            var location = RexlCloudHelper.serverLocationToLocation(player.getWorld(), packet.getServerLocation());
                            if (location.getWorld() != null) {
                                Bukkit.getScheduler().runTask(Bridge.getInstance(), () -> {
                                    player.teleport(location);
                                });
                            }
                        } else if (packet.getBufferTime() > 0) {
                            packet.setBufferTime(packet.getBufferTime() + System.currentTimeMillis());
                            PlayerJoinListener.getBufferedPlayerTeleports().add(packet);
                        }
                    }
                    case API_REQUEST_GET_PLAYER_LOCATION -> {
                        var packet = (ApiRequestPacketToSpecific<String>) packetContainer.getPacket();
                        var player = Bukkit.getPlayer(UUID.fromString(packet.getData()[0]));
                        if (player != null) {
                            var location = RexlCloudHelper.locationToServerLocation(player.getLocation());
                            var newPacket = new ApiResponsePacket(packet.getRequestId(), packet.getServer(), location);
                            var newPacketContainer = new PacketContainer(PacketType.API_REQUEST_GET_PLAYER_LOCATION, newPacket);
                            Bridge.getInstance().getRexlCloudApi().getChannel().writeAndFlush(newPacketContainer);
                        }
                    }
                }
                if (Bridge.getInstance().getRexlCloudApi() != null) {
                    Bridge.getInstance().getRexlCloudApi().receivePacket(packetContainer);
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                ReferenceCountUtil.release(msg);
            }

        });
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) {
        var serverName = Bridge.getInstance().getServerName();
        var container = new PacketContainer(PacketType.SERVER_ONLINE,
                new ServerOnlinePacket(serverName, Bridge.getInstance().getWrapperToken()));
        ctx.channel().writeAndFlush(container);
        Bridge.getInstance().getWrapperReconnect().connected();
        Bridge.getInstance().setRexlCloudApi(RexlCloudApi.connect(ctx.channel(), 100, serverName));
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        // The wrapper went away (e.g. it restarts while its servers keep running): connect again for a while
        if (!isStopping) {
            Bridge.getInstance().getWrapperReconnect().lost();
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
