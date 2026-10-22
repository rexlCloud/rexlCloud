package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerPortInUsePacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.core.server.ServerLifecycle;
import de.rexlcloud.master.RexlCloudMaster;
import io.netty.channel.ChannelHandlerContext;

/**
 * A wrapper could not start a server because its port is taken: no server gets that port again, and the server
 * is started again with the next free one.
 */
public class ServerPortInUseHandler implements IPacketHandler<ServerPortInUsePacket> {

    @Override
    public PacketType getType() {
        return PacketType.SERVER_PORT_IN_USE;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ServerPortInUsePacket packet) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        serverManager.getServerByNameAndKey(packet.getName(), packet.getKey())
                .ifPresent(server -> {
                    serverManager.blockPort(packet.getPort());
                    server.setPort(serverManager.getServerPort(server.getGroup()));
                    Console.warn("Port %s is already in use on the wrapper, %s starts again with port %s",
                            packet.getPort(), server.getName(), server.getPort());
                    // The server manager starts it again within a few seconds
                    server.moveTo(ServerLifecycle.WAITING);
                });
    }
}
