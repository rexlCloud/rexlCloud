package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ProxyAddServerPacket;
import de.rexlcloud.core.network.packets.ProxyRemoveServerPacket;
import de.rexlcloud.core.network.packets.ServerInfoPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.core.server.ServerLifecycle;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.master.RexlCloudMaster;
import io.netty.channel.ChannelHandlerContext;

public class ServerInfoHandler implements IPacketHandler<ServerInfoPacket> {
    @Override
    public PacketType getType() {
        return PacketType.SERVER_INFO;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ServerInfoPacket packet) {
        Console.debug("SERVER_INFO");
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        serverManager.getServerByName(packet.getServerName()).ifPresent(server -> {
            var online = server.isOnline();
                    if (packet.isOnline() && server.isStarting()) {
                        server.moveTo(ServerLifecycle.ONLINE);
                    }
                    server.setState(packet.getState());
                    if (!online && packet.isOnline()) {
                        if (server.getGroup().getType() == ServerType.PROXY) {
                            RexlCloudMaster.getInstance()
                                    .getServerManager()
                                    .getServers()
                                    .stream()
                                    .filter(otherServer -> otherServer.getGroup().getType() == ServerType.SERVER)
                                    .filter(AbstractServer::isOnline)
                                    .forEach(otherServer -> {
                                        var proxyAddServerPacket = new ProxyAddServerPacket(
                                                otherServer.getName(),
                                                otherServer.getWrapper().getAddress(),
                                                otherServer.getPort(),
                                                otherServer.getGroup().isFallback());
                                        var packetContainerSend = new PacketContainer(
                                                PacketType.PROXY_ADD_SERVER,
                                                proxyAddServerPacket);
                                        server.getWrapper().getChannel().write(packetContainerSend);
                                        Console.debug("ADD_SERVER %s TO %s", otherServer.getName(), server.getName());
                                    });
                            server.getWrapper().getChannel().flush();
                        } else {
                            RexlCloudMaster.getInstance()
                                    .getServerManager()
                                    .getServers()
                                    .stream()
                                    .filter(otherServer -> otherServer.getGroup().getType() == ServerType.PROXY)
                                    .filter(AbstractServer::isOnline)
                                    .forEach(otherServer -> {
                                        var proxyAddServerPacket = new ProxyAddServerPacket(
                                                server.getName(),
                                                server.getWrapper().getAddress(),
                                                server.getPort(),
                                                server.getGroup().isFallback());
                                        var packetContainerSend = new PacketContainer(
                                                PacketType.PROXY_ADD_SERVER,
                                                proxyAddServerPacket);
                                        otherServer.getWrapper().getChannel().writeAndFlush(packetContainerSend);
                                        Console.debug("ADD_SERVER %s TO %s",
                                                server.getName(),
                                                otherServer.getName());
                                    });
                        }
                    } else if (online && !packet.isOnline()) {
                        if (server.getGroup().getType() == ServerType.SERVER) {
                            RexlCloudMaster.getInstance()
                                    .getServerManager()
                                    .getServers()
                                    .stream()
                                    .filter(otherServer -> otherServer.getGroup().getType() == ServerType.PROXY)
                                    .filter(AbstractServer::isOnline)
                                    .forEach(otherServer -> {
                                        var proxyAddServerPacket = new ProxyRemoveServerPacket(server.getName());
                                        var packetContainerSend = new PacketContainer(
                                                PacketType.PROXY_REMOVE_SERVER,
                                                proxyAddServerPacket);
                                        otherServer.getWrapper().getChannel().writeAndFlush(packetContainerSend);
                                        Console.debug("REMOVE_SERVER %s FROM %s", server.getName(), otherServer.getName());
                                    });
                        }
                    }
                    server.sendApiUpdate();
                });
    }
}
