package de.rexlcloud.bridge.networking;

import de.rexlcloud.bridge.RexlCloudBridge;
import de.rexlcloud.bridge.helper.RexlCloudHelper;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.SerialExecutor;
import de.rexlcloud.core.network.packets.PlayerTeleportPacket;
import de.rexlcloud.core.network.packets.ServerOnlinePacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacketToSpecific;
import de.rexlcloud.core.network.packets.api.ApiResponsePacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import net.minestom.server.MinecraftServer;

import java.io.IOException;
import java.util.UUID;

public class ServerHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * Handles this connection's packets in order, without a new thread per packet.
     */
    private final SerialExecutor packetQueue = new SerialExecutor();

    private volatile boolean isStopping = false;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        packetQueue.execute(() -> {
            try {
                var packetContainer = (PacketContainer) msg;
                var bridge = RexlCloudBridge.getInstance();
                switch (packetContainer.getPacketType()) {
                    case SERVER_STOP -> {
                        System.out.println("[rexlCloud] The server is stopped by the cloud");
                        isStopping = true;
                        bridge.shutdownServer();
                    }
                    case PLAYER_TELEPORT -> {
                        var packet = (PlayerTeleportPacket) packetContainer.getPacket();
                        var player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(packet.getUuid());
                        if (player != null) {
                            RexlCloudHelper.teleport(player, packet.getServerLocation());
                        } else if (packet.getBufferTime() > 0) {
                            // The player is still joining
                            packet.setBufferTime(packet.getBufferTime() + System.currentTimeMillis());
                            bridge.getBufferedPlayerTeleports().add(packet);
                        }
                    }
                    case API_REQUEST_GET_PLAYER_LOCATION -> {
                        @SuppressWarnings("unchecked")
                        var packet = (ApiRequestPacketToSpecific<String>) packetContainer.getPacket();
                        var player = MinecraftServer.getConnectionManager()
                                .getOnlinePlayerByUuid(UUID.fromString(packet.getData()[0]));
                        if (player != null && bridge.getRexlCloudApi() != null) {
                            var location = RexlCloudHelper.posToServerLocation(player.getInstance(), player.getPosition());
                            var response = new ApiResponsePacket(packet.getRequestId(), packet.getServer(), location);
                            bridge.getRexlCloudApi().getChannel().writeAndFlush(
                                    new PacketContainer(PacketType.API_REQUEST_GET_PLAYER_LOCATION, response));
                        }
                    }
                    default -> {
                    }
                }
                if (bridge.getRexlCloudApi() != null) {
                    bridge.getRexlCloudApi().receivePacket(packetContainer);
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                ReferenceCountUtil.release(msg);
            }
        });
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        var bridge = RexlCloudBridge.getInstance();
        ctx.channel().writeAndFlush(new PacketContainer(PacketType.SERVER_ONLINE,
                new ServerOnlinePacket(bridge.getServerName(), bridge.getWrapperToken())));
        bridge.getWrapperReconnect().connected();
        bridge.setRexlCloudApi(RexlCloudApi.connect(ctx.channel(), 100, bridge.getServerName()));
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        // The wrapper went away (e.g. it restarts while its servers keep running): connect again for a while
        if (!isStopping) {
            RexlCloudBridge.getInstance().getWrapperReconnect().lost();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            ctx.close();
            return;
        }
        cause.printStackTrace();
    }

}
