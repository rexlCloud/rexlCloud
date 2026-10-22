package de.rexlcloud.wrapper.network.handling;

import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.wrapper.network.handling.handler.*;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.function.Predicate;

public class WrapperPacketHandler {

    private static final ArrayList<IPacketHandler<? extends Packet>> handlers = new ArrayList<>();

    static {
        //Server
        handlers.add(new ServerExecuteHandler());
        handlers.add(new ServerKillHandler());
        handlers.add(new ServerInstallJarHandler());
        handlers.add(new ServerLogHandler());
        handlers.add(new ServerStartHandler());
        handlers.add(new ServerStopHandler());
        handlers.add(new ServerSaveTemplateHandler());
        handlers.add(new ServerSyncMessagesHandler());
        handlers.add(new ServerTemplateHandler());
        handlers.add(new TemplateDeleteHandler());
        handlers.add(new TemplateStateRequestHandler());

        //Wrapper
        handlers.add(new WrapperStopHandler());
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
                .findFirst().ifPresent(packetHandler -> packetHandler.handle(ctx, packetContainer.getPacket()));
    }

}
