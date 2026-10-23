package de.rexlcloud.master.network.handling;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.handler.*;
import de.rexlcloud.master.network.handling.handler.api.*;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.function.Predicate;

public class MasterPacketHandler {

    private static final ArrayList<IPacketHandler<? extends Packet>> handlers = new ArrayList<>();

    static {
        //Wrapper
        handlers.add(new WrapperConnectHandler());
        handlers.add(new WrapperInfoHandler());
        handlers.add(new TemplateStateHandler());
        handlers.add(new TemplateUploadHandler());
        handlers.add(new WrapperStopHandler());

        //Server
        handlers.add(new ServerStopHandler());
        handlers.add(new ServerPortInUseHandler());
        handlers.add(new ServerInfoHandler());
        handlers.add(new ServerConsoleHandler());

        //Player
        handlers.add(new PlayerJoinHandler());
        handlers.add(new PlayerQuitHandler());
        handlers.add(new PlayerServerChangeHandler());

        //API
        handlers.add(new ExecuteOnServerHandler());
        handlers.add(new GetPlayerLocationHandler());
        handlers.add(new GetServerGroupHandler());
        handlers.add(new GetServerGroupsHandler());
        handlers.add(new GetServerHandler());
        handlers.add(new GetServersHandler());
        handlers.add(new GetWrapperHandler());
        handlers.add(new GetWrappersHandler());
        handlers.add(new KickPlayerHandler());
        handlers.add(new MessagePlayerHandler());
        handlers.add(new PlayerChangeServerHandler());
        handlers.add(new PlayerChangeServerTeleportHandler());
        handlers.add(new PlayerDataUpdateHandler());
        handlers.add(new GetOfflinePlayerHandler());
        handlers.add(new PlayerTeleportHandler());
        handlers.add(new StartServerHandler());
        handlers.add(new StopServerHandler());
        handlers.add(new WrapperStopHandler());
        handlers.add(new ServerStateChangeHandler());
    }

    public static void addHandler(IPacketHandler<? extends Packet> handler) {
        handlers.add(handler);
    }

    public static void addHandlerFirst(IPacketHandler<? extends Packet> handler) {
        handlers.add(0, handler);
    }

    public static void removeHandler(IPacketHandler<? extends Packet> handler) {
        handlers.remove(handler);
    }

    public static void removeHandlerIf(Predicate<IPacketHandler<? extends Packet>> filter) {
        handlers.removeIf(filter);
    }

    public static void handlePacket(ChannelHandlerContext ctx, PacketContainer packetContainer) {
        new ArrayList<>(handlers).stream().filter(packetHandler -> packetHandler.getType().equals(packetContainer.getPacketType()))
                .findFirst().ifPresent(packetHandler -> {
                    if (!packetHandler.requiresRegisteredWrapper()) {
                        packetHandler.handle(ctx, packetContainer.getPacket());
                        return;
                    }
                    RexlCloudMaster.getInstance().getWrapperManager().getWrapperByChannel(ctx.channel()).ifPresentOrElse(wrapper -> {
                        packetHandler.handle(ctx, packetContainer.getPacket());
                        if (packetHandler instanceof WrapperPacketHandler<? extends Packet> handler) {
                            handler.handle(ctx, wrapper, packetContainer.getPacket());
                        }
                    }, () -> {
                        Console.error("Got packet %s from %s before it authenticated!",
                                packetContainer.getPacketType().toString(), ctx.channel().remoteAddress());
                        ctx.channel().disconnect();
                        ctx.channel().close();
                    });
                });
    }

}
