package de.rexlcloud.master.commands;

import de.rexlcloud.core.command.Argument;
import de.rexlcloud.core.command.CommandDescription;
import de.rexlcloud.core.command.CommandMethod;
import de.rexlcloud.core.command.Suggestion;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerExecuteCommandPacket;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.server.Server;

import java.util.List;

public class ServerCommands {

    private static final String BLANK_SPACE_PLACEHOLDER = "%%bs%%";

    @CommandMethod("server stop <server>")
    public void stopServer(@Argument(value = "server", suggestions = "serverNameOnline") String serverName) {
        RexlCloudMaster.getInstance().getServerManager().getOnlineServerByName(serverName).ifPresentOrElse(server -> {
            if (!server.isStarted()) {
                Console.error("The server %s does not exist!", serverName);
                return;
            }
            var wrapper = server.getWrapper();
            var serverStopPacket = new ServerStopPacket(server.getName(), server.getKey());
            var packetContainer = new PacketContainer(PacketType.SERVER_STOP, serverStopPacket);
            wrapper.getChannel().writeAndFlush(packetContainer);
            RexlCloudMaster.getInstance().getServerManager().stopServer(server);
            Console.write("Stopping the server %s!", server.getName());
        }, () -> Console.error("The server %s does not exist!", serverName));
    }


    @CommandMethod("server kill <server>")
    public void killServer(@Argument(value = "server", suggestions = "serverNameStarted") String serverName) {
        RexlCloudMaster.getInstance().getServerManager().getStartedNotOnlineServer(serverName).ifPresentOrElse(server -> {
            if (!server.isStarted()) {
                Console.error("The server %s does not exist!", serverName);
                return;
            }
            var wrapper = server.getWrapper();
            var serverStopPacket = new ServerStopPacket(server.getName(), server.getKey());
            var packetContainer = new PacketContainer(PacketType.SERVER_STOP, serverStopPacket);
            wrapper.getChannel().writeAndFlush(packetContainer);
            RexlCloudMaster.getInstance().getServerManager().stopServer(server);
            Console.write("Killing the server %s!", server.getName());
        }, () -> Console.error("The server %s does not exist!", serverName));
    }

    @CommandMethod("server start <group> <amount>")
    public void startGroup(@Argument(value = "group", suggestions = "serverGroup") String group, @Argument("amount") int amount) {
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(group).ifPresentOrElse(serverGroup -> {
            if (amount < 1) {
                Console.error("At least 1 server has to be started!");
                return;
            }
            if (serverGroup.isStaticServer()) {
                Console.error("Only 1 server of %s can run, because %s is static!", serverGroup.getName(), serverGroup.getName());
                return;
            }
            for (int i = 0; i < amount; i++) {
                var server = new Server(RexlCloudMaster.getInstance().getServerManager().getServerNameForNextServer(serverGroup), serverGroup);
                RexlCloudMaster.getInstance().getServerManager().startServer(server);
            }
            Console.write("Starting %s servers of %s", amount, serverGroup.getName());
        }, () -> Console.error("The group %s does not exist!", group));
    }

    @CommandMethod("server execute <server> <command>")
    public void executeCommand(@Argument(value = "server", suggestions = "serverNameOnline") String serverName, @Argument(value = "command", greedy = true) String command) {
        RexlCloudMaster.getInstance().getServerManager().getServerByName(serverName).ifPresentOrElse(server -> {
            if (!server.isStarted()) {
                Console.error("The server %s does not exist!", serverName);
                return;
            }
            var wrapper = server.getWrapper();
            var cmd = command.replace(BLANK_SPACE_PLACEHOLDER, " ");
            var serverExecutePacket = new ServerExecuteCommandPacket(server.getName(), cmd);
            var packetContainer = new PacketContainer(PacketType.SERVER_EXECUTE_COMMAND, serverExecutePacket);
            wrapper.getChannel().writeAndFlush(packetContainer);
            Console.info("On %s, ran the command %s!", server.getName(), cmd);

        }, () -> Console.error("The server %s does not exist!", serverName));
    }

    @CommandMethod("server save <server>")
    @CommandDescription("Save the files of a running server into its group's default template")
    public void saveToTemplate(@Argument(value = "server", suggestions = "serverNameOnline") String serverName) {
        saveToTemplate(serverName, "default");
    }

    @CommandMethod("server save <server> <variant>")
    @CommandDescription("Save the files of a running server into a template variant of its group, e.g. a map")
    public void saveToTemplate(@Argument(value = "server", suggestions = "serverNameOnline") String serverName,
                               @Argument("variant") String variant) {
        var server = RexlCloudMaster.getInstance().getServerManager().getServerByName(serverName).orElse(null);
        if (server == null) {
            Console.error("The server %s does not exist!", serverName);
            return;
        }
        Console.info("Saving %s into the template %s/%s...", serverName, server.getGroup().getName(), variant);
        // The result is logged by the template uploads; only failures before the upload need a line here
        RexlCloudMaster.getInstance().getTemplateUploads().save(server, variant).exceptionally(e -> {
            if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
                Console.error("%s cannot be saved: %s", serverName, e.getMessage());
            }
            return null;
        });
    }

    @Suggestion("serverName")
    public List<String> serverNames() {
        return RexlCloudMaster.getInstance().getServerManager().getServers().stream().map(AbstractServer::getName).toList();
    }

    @Suggestion("serverNameOnline")
    public List<String> serverNamesOnline() {
        return RexlCloudMaster.getInstance().getServerManager().getServers().stream().filter(AbstractServer::isOnline).map(AbstractServer::getName).toList();
    }

    @Suggestion("serverNameStarted")
    public List<String> serverNamesStarted() {
        return RexlCloudMaster.getInstance().getServerManager().getServers().stream()
                .filter(server -> !server.isOnline())
                .filter(AbstractServer::isStarted)
                .map(AbstractServer::getName).toList();
    }

}
