package de.rexlcloud.wrapper.commands;

import de.rexlcloud.core.command.Argument;
import de.rexlcloud.core.command.CommandDescription;
import de.rexlcloud.core.command.CommandMethod;
import de.rexlcloud.core.command.Suggestion;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.wrapper.RexlCloudWrapper;

import java.util.List;

public class Commands {

    @CommandMethod("stop")
    public void stopCommand() {
        stop();
    }

    @CommandMethod("detach")
    @CommandDescription("Exit the wrapper and leave its servers running (only with detachedServers)")
    public void detachCommand() {
        if (!RexlCloudWrapper.getInstance().isDetachedServers()) {
            Console.error("The servers are child processes of the wrapper; set detachedServers in wrapper.json to keep them running");
            return;
        }
        // The shutdown leaves the servers running in detached mode
        System.exit(0);
    }

    @CommandMethod("clear")
    public void clearCommand() {
        Console.clear();
    }

    public static void stop() {
        if (RexlCloudWrapper.getInstance().getWrapperClient().getChannel() != null
                && RexlCloudWrapper.getInstance().getWrapperClient().getChannel().isOpen()) {
            PacketContainer packetContainer = new PacketContainer(PacketType.WRAPPER_STOP, null);
            RexlCloudWrapper.getInstance().getWrapperClient().getChannel().writeAndFlush(packetContainer);
        }
        Console.write("Stopping all servers");
        RexlCloudWrapper.getInstance().getServerManager().stopAllServers().whenComplete((unused, unused2) -> {
            System.exit(0);
        });
    }

    @CommandMethod("console <server>")
    @CommandDescription("Toggles the server console output")
    public static void toggleConsoleLog(@Argument(value = "server", suggestions = "wrapperServer") String serverName) {
        RexlCloudWrapper.getInstance().getServerManager().getServerByName(serverName).ifPresentOrElse(server -> {
            if (!server.isWriteLog()) {
                Console.info("Showing the console of %s", server.getName());
            } else {
                Console.info("No longer showing the console of %s", server.getName());
            }
            server.setWriteLog(!server.isWriteLog());
        }, () -> Console.error("The server %s does not exist", serverName));
    }

    @Suggestion("wrapperServer")
    public List<String> getWrapperServer() {
        return RexlCloudWrapper.getInstance().getServerManager().getServers().stream().map(AbstractServer::getName)
                .toList();
    }

}
