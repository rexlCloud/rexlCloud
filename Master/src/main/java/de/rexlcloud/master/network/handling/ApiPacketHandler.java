package de.rexlcloud.master.network.handling;

import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.core.network.packets.api.ApiResponsePacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.master.RexlCloudMaster;
import io.netty.channel.ChannelHandlerContext;

public interface ApiPacketHandler<T> extends IPacketHandler<ApiRequestPacket<T>> {

    default void handle(ChannelHandlerContext ctx, Object o) {
        try {
            this.handlePacket(ctx, o == null ? null : (ApiRequestPacket<T>) o);
        } catch (Exception e) {
            if (RexlCloudMaster.getInstance().isDebug()) {
                e.printStackTrace();
            }
        }
    }

    void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<T> packet);

    default void response(ApiRequestPacket<T> packet, Object data) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        serverManager.getServerByName(packet.getServer()).ifPresent(server -> {
            var responsePacket = new ApiResponsePacket(packet.getRequestId(), server.getName(), data);
            var packetContainerOut = new PacketContainer(getType(), responsePacket);
            if (server.getWrapper() != null && server.getWrapper().getChannel() != null) {
                server.getWrapper().getChannel().writeAndFlush(packetContainerOut);
            }
        });
    }

}
