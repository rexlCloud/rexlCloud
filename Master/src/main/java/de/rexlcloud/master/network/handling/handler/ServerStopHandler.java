package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.master.RexlCloudMaster;
import io.netty.channel.ChannelHandlerContext;

public class ServerStopHandler implements IPacketHandler<ServerStopPacket> {
    @Override
    public PacketType getType() {
        return PacketType.SERVER_STOP;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ServerStopPacket packet) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        // Only this run of the server: a late report about an earlier run must not stop the current one
        serverManager.getServerByNameAndKey(packet.getName(), packet.getKey()).ifPresent(server -> {
            if (server.isStarted()) {
                // Crashed or failed to start; the server manager decides whether it is started again
                serverManager.stopServer(server);
            }
            serverManager.finishStop(server);
        });
    }
}
