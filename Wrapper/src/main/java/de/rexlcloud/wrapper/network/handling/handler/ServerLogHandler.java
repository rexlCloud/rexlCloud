package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import io.netty.channel.ChannelHandlerContext;

public class ServerLogHandler implements IPacketHandler<Packet> {
    @Override
    public PacketType getType() {
        return PacketType.SERVER_LOG;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Object o) {
        var serverName = (String) o;
        RexlCloudWrapper.getInstance().getServerManager().getServerByName(serverName)
                .ifPresent(server -> {
                    PacketContainer container = new PacketContainer(PacketType.SERVER_LOG,
                            server.getLogs());
                    RexlCloudWrapper.getInstance().getWrapperClient().getChannel()
                            .writeAndFlush(container);
                });
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, Packet packet) {
        //not used
    }

}
