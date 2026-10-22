package de.rexlcloud.master.api;

import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.core.network.packets.api.ApiResponsePacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.server.Server;

public class ApiMethods {

    public static void handleApiResponse(PacketContainer packetContainerIn) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        var packet = (ApiResponsePacket) packetContainerIn.getPacket();
        serverManager.getServerByName(packet.getServer()).ifPresent(server -> server.sendPacket(packetContainerIn));
    }

    private static void response(Server server, PacketContainer packetContainerIn, Object data) {
        var apiRequestPacket = (ApiRequestPacket<?>) packetContainerIn.getPacket();
        var responsePacket = new ApiResponsePacket(apiRequestPacket.getRequestId(), server.getName(), data);
        var packetContainerOut = new PacketContainer(packetContainerIn.getPacketType(), responsePacket);
        if (server.getWrapper() != null && server.getWrapper().getChannel() != null) {
            server.getWrapper().getChannel().writeAndFlush(packetContainerOut);
        }
    }

}
