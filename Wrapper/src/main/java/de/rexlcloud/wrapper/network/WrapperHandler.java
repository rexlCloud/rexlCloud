package de.rexlcloud.wrapper.network;

import com.sun.management.OperatingSystemMXBean;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.SerialExecutor;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerInfoPacket;
import de.rexlcloud.core.network.packets.WrapperConnectPacket;
import de.rexlcloud.core.network.packets.WrapperInfoPacket;
import de.rexlcloud.core.network.packets.api.ApiResponsePacket;
import de.rexlcloud.core.network.packets.api.ApiUpdateObjectPacket;
import de.rexlcloud.core.network.packets.types.WrapperToProxyPacket;
import de.rexlcloud.core.network.packets.types.WrapperToServerPacket;
import de.rexlcloud.core.network.packets.types.WrapperToSpecific;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import de.rexlcloud.wrapper.SystemMemory;
import de.rexlcloud.wrapper.network.handling.WrapperPacketHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Timer;
import java.util.TimerTask;

public class WrapperHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * The master's packets in the order they were sent: e.g. a server never starts before its template is written,
     * and a stop never overtakes the start of the same server.
     */
    private final SerialExecutor packetQueue = new SerialExecutor();

    private boolean connected = false;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        packetQueue.execute(() -> {
            try {
                var packetContainer = (PacketContainer) msg;
                if (packetContainer.getPacket() instanceof ApiResponsePacket responsePacket) {
                    Console.debug("API_RESPONSE >> " + packetContainer.getPacketType());
                    var serverByName = responsePacket.getServer();
                    RexlCloudWrapper.getInstance().getServerManager().getServerByName(serverByName)
                            .ifPresent(server -> server.sendPacket(packetContainer));
                } else if (packetContainer.getPacket() instanceof ApiUpdateObjectPacket responsePacket) {
                    Console.debug("API_UPDATE_OBJECT >> " + packetContainer.getPacketType());
                    var serverByName = responsePacket.getTargetServer();
                    RexlCloudWrapper.getInstance().getServerManager().getServerByName(serverByName)
                            .ifPresent(server -> server.sendPacket(packetContainer));
                }
                if (packetContainer.getPacket() instanceof WrapperToSpecific specificPacket) {
                    RexlCloudWrapper.getInstance().getServerManager().getServerByName(specificPacket.getTargetServerName())
                            .ifPresent(server -> server.sendPacket(packetContainer));
                }
                if (packetContainer.getPacket() instanceof WrapperToServerPacket) {
                    RexlCloudWrapper.getInstance().getServerManager().sendPacketToAllServers(ServerType.SERVER,
                            packetContainer);
                }
                if (packetContainer.getPacket() instanceof WrapperToProxyPacket) {
                    RexlCloudWrapper.getInstance().getServerManager().sendPacketToAllServers(ServerType.PROXY,
                            packetContainer);
                }
                if (packetContainer.getPacketType() == null) {
                    return;
                }
                WrapperPacketHandler.handlePacket(ctx, packetContainer);
            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                ReferenceCountUtil.release(msg);
            }
        });
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) {
        // Servers that kept running while the master was away; the master takes them over
        var running = RexlCloudWrapper.getInstance().getServerManager().getServers().stream()
                .filter(server -> server.isStarting() || server.isOnline())
                .map(server -> new WrapperConnectPacket.RunningServer(server.getName(), server.getKey(),
                        server.getGroup().getName(), server.getPort(), server.isOnline()))
                .toList();
        var connectPacket = new WrapperConnectPacket(RexlCloudWrapper.getInstance().getWrapperConfig().getAuthKey(), running);
        var container = new PacketContainer(PacketType.WRAPPER_CONNECT, connectPacket);
        ctx.channel().writeAndFlush(container);
        connected = true;
        // Their state, so the master sees the online ones as online again. Starting ones (e.g. attached after a
        // restart, until their plugin connects) report themselves when they are online
        RexlCloudWrapper.getInstance().getServerManager().getServers().stream()
                .filter(server -> server.isOnline())
                .forEach(server -> ctx.channel().writeAndFlush(new PacketContainer(PacketType.SERVER_INFO,
                        new ServerInfoPacket(server.getName(), server.isOnline(), server.getState()))));
        new Thread(() -> new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!connected) {
                    cancel();
                    return;
                }
                var cpu = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getCpuLoad();
                // Available memory includes the page cache the kernel gives back when servers need it
                var memory = SystemMemory.read();
                var wrapperInfoPacket = new WrapperInfoPacket(cpu, memory.used(), memory.available(), memory.total(),
                        RexlCloudWrapper.getInstance().getJavaRuntimes().get());
                ctx.channel().writeAndFlush(new PacketContainer(PacketType.WRAPPER_INFO, wrapperInfoPacket));
            }
        }, 1000, 5000)).start();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        connected = false;
        // The servers keep running: when the master is back (e.g. after an update) it takes them over
        if (!RexlCloudWrapper.getInstance().getServerManager().getServers().isEmpty()) {
            Console.info("The servers keep running until the master is back");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            connected = false;
            Console.info("The connection to the master was lost!");
            return;
        }
        cause.printStackTrace();
    }

}
