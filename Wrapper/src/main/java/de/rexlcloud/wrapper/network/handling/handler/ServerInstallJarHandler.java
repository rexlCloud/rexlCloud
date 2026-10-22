package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.InstallServerJarPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.core.server.files.ServerJarInstaller;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Installs the server jar of a static group into its template on this wrapper.
 */
public class ServerInstallJarHandler implements IPacketHandler<InstallServerJarPacket> {

    @Override
    public PacketType getType() {
        return PacketType.INSTALL_SERVER_JAR;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, InstallServerJarPacket packet) {
        var serverGroup = packet.getServerGroup();
        // Static servers copy the jar from here into their own folder on start
        var installFolder = Path.of("servers", "templates", serverGroup.getName(), "default");
        var jar = installFolder.resolve(serverGroup.getType() == ServerType.SERVER ? "server.jar" : "proxy.jar");
        try {
            ServerJarInstaller.inWorkingDirectory().install(packet.getServerJar(), jar);
        } catch (IOException e) {
            Console.error("The server jar for %s could not be installed: %s", serverGroup.getName(), e.getMessage());
        }
    }

}
