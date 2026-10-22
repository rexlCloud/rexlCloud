package de.rexlcloud.master.network;

import de.rexlcloud.core.api.events.events.PacketReceivedEvent;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.SerialExecutor;
import de.rexlcloud.core.network.packets.api.ApiResponsePacket;
import de.rexlcloud.core.network.packets.types.ServerToMasterPacket;
import de.rexlcloud.core.network.packets.types.WrapperToSpecific;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.api.ApiMethods;
import de.rexlcloud.master.network.handling.MasterPacketHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class WrapperHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * Handles this connection's packets in order, without a new thread per packet.
     */
    private final SerialExecutor packetQueue = new SerialExecutor();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        packetQueue.execute(() -> {
            try {
                var packetContainer = ((PacketContainer) msg);
                var event = new PacketReceivedEvent(packetContainer);
                RexlCloudMaster.getInstance().getEventManager().callEvent(event);
                if (event.isCanceled()) {
                    return;
                }
                if (packetContainer.getPacket() instanceof ApiResponsePacket) {
                    ApiMethods.handleApiResponse(packetContainer);
                } else if (packetContainer.getPacket() instanceof ServerToMasterPacket
                        && packetContainer.getPacket() instanceof WrapperToSpecific packet) {
                    RexlCloudMaster.getInstance().getServerManager().getServerByName(packet.getTargetServerName())
                            .ifPresentOrElse(server -> server.sendPacket(packetContainer),
                                    () -> Console.debug("Can't send custom packet message to %s. server does not exist", packet.getTargetServerName()));
                }

                //new handle
                MasterPacketHandler.handlePacket(ctx, packetContainer);

            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                ReferenceCountUtil.release(msg);
            }
        });
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext ctx) {
        if (RexlCloudMaster.getInstance().getWrapperManager().getWrapperRegistry().values().stream()
                .noneMatch(t -> t.getAddress().equals(ctx.channel().remoteAddress().toString().split(":")[0].replace("/", "")))) {
            ctx.disconnect();
            ctx.close();
            return;
        }
        // A wrapper has two seconds to authenticate
        ctx.executor().schedule(() -> {
            if (RexlCloudMaster.getInstance().getWrapperManager().getWrapperByChannel(ctx.channel()).isEmpty()) {
                Console.error("Disconnected %s, it did not authenticate!",
                        ctx.channel().remoteAddress().toString().replace("/", "").split(":")[0]);
                ctx.channel().disconnect();
                ctx.channel().close();
            }
        }, 2, TimeUnit.SECONDS);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        RexlCloudMaster.getInstance().getWrapperManager().getWrapperByChannel(ctx.channel())
                .ifPresent(RexlCloudMaster.getInstance().getWrapperManager()::unregisterWrapper);
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException) {
            RexlCloudMaster.getInstance().getWrapperManager().getWrapperByChannel(ctx.channel())
                    .ifPresent(RexlCloudMaster.getInstance().getWrapperManager()::unregisterWrapper);
            return;
        }
        cause.printStackTrace();
    }

}
