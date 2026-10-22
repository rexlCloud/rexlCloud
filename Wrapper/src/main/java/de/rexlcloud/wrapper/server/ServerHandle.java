package de.rexlcloud.wrapper.server;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.server.ServerLifecycle;
import de.rexlcloud.core.network.SerialExecutor;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.types.ServerToMasterPacket;
import de.rexlcloud.core.network.packets.ServerOnlinePacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;

public class ServerHandle extends SimpleChannelInboundHandler<Object> {

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
                if (packetContainer.getPacketType() == PacketType.SERVER_ONLINE) {
                    authenticate(ctx, packetContainer);
                    return;
                }
                // Anything else only from a plugin that proved which server it belongs to
                var server = RexlCloudWrapper.getInstance().getServerConnectionManager().getServerFromChannel(ctx.channel());
                if (server.isEmpty()) {
                    Console.warn("Dropped %s from an unknown connection %s", packetContainer.getPacketType(),
                            ctx.channel().remoteAddress());
                    ctx.channel().close();
                    return;
                }
                if (packetContainer.getPacket() instanceof ApiRequestPacket<?> request) {
                    Console.debug("API_REQUEST >> " + packetContainer.getPacketType());
                    // A plugin can only act as its own server
                    request.setServer(server.get().getName());
                    if (RexlCloudWrapper.getInstance().getWrapperClient().getChannel() != null) {
                        RexlCloudWrapper.getInstance().getWrapperClient().getChannel().writeAndFlush(packetContainer);
                    }
                } else if (packetContainer.getPacket() instanceof ServerToMasterPacket) {
                    Console.debug("PACKET_TO_MASTER >> " + packetContainer.getPacketType());
                    if (RexlCloudWrapper.getInstance().getWrapperClient().getChannel() != null) {
                        RexlCloudWrapper.getInstance().getWrapperClient().getChannel().writeAndFlush(packetContainer);
                    }
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                ReferenceCountUtil.release(msg);
            }

        });
    }

    /**
     * The plugin's first packet: the server's name and the token from its cloud.json. Only the run that is starting
     * with exactly that token is connected; anything else is disconnected.
     */
    private void authenticate(ChannelHandlerContext ctx, PacketContainer packetContainer) {
        if (!(packetContainer.getPacket() instanceof ServerOnlinePacket packet)) {
            Console.warn("A plugin of an older version tried to connect from %s; servers get the current plugin when "
                    + "they start", ctx.channel().remoteAddress());
            ctx.channel().close();
            return;
        }
        // The run that is starting, or an online one whose plugin lost its connection (e.g. the wrapper restarted)
        RexlCloudWrapper.getInstance().getServerManager().getServers().stream()
                .filter(server -> server.getName().equals(packet.getName()))
                .filter(server -> server.isStarting()
                        || (server.isOnline() && (server.getChannel() == null || !server.getChannel().isActive())))
                .filter(server -> server.checkConnectToken(packet.getToken()))
                .findFirst()
                .ifPresentOrElse(server -> {
                    Console.info(server.isOnline() ? "Server %s connected to wrapper again" : "Server %s connected to wrapper",
                            server.getName());
                    server.setChannel(ctx.channel());
                    server.moveTo(ServerLifecycle.ONLINE);
                    server.sendServerInfo();
                    server.getChannel().writeAndFlush(new PacketContainer(PacketType.SYNC_MESSAGES,
                            RexlCloudWrapper.getInstance().getPluginMessageConfig()));
                }, () -> {
                    Console.error("Refused a connection as %s: no server of that name is starting with that token",
                            packet.getName());
                    ctx.channel().close();
                });
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        // The server's plugin disconnected: the server is going down
        RexlCloudWrapper.getInstance().getServerConnectionManager().getServerFromChannel(ctx.channel()).ifPresent(Server::stop);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            RexlCloudWrapper.getInstance().getServerConnectionManager().getServerFromChannel(ctx.channel()).ifPresent(Server::stop);
            return;
        }
        cause.printStackTrace();
    }
}