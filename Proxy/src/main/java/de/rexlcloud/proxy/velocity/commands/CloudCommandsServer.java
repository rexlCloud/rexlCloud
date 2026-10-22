package de.rexlcloud.proxy.velocity.commands;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotation.specifier.Range;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.api.exceptions.ServerGroupNotFoundException;
import de.rexlcloud.core.api.exceptions.ServerNotFoundException;
import de.rexlcloud.proxy.common.config.PluginMessages;
import de.rexlcloud.proxy.common.messages.MessageParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Arrays;
import java.util.List;

import static de.rexlcloud.core.config.MessageKey.*;

public class CloudCommandsServer {

    @Command("rexlcloud|cloud server start <group> <amount>")
    @Permission("rexlcloud.server.start")
    public void startServer(CommandSource commandSource, @Argument("group") String group, @Argument(value = "amount") @Range(min = "1") int amount) {
        RexlCloudApi.getInstance().getServerGroup(group).thenAccept(serverGroup -> {
            RexlCloudApi.getInstance().startServer(serverGroup, amount).thenAccept(response -> {
                commandSource.sendMessage(PluginMessages.getInstance().getMessage(START_SERVER_MESSAGE,
                        Placeholder.parsed("amount", amount + ""),
                        Placeholder.parsed("group", serverGroup.getName())
                ));
            }).exceptionally(throwable -> {
                commandSource.sendMessage(PluginMessages.getInstance().getMessage(ERROR_WILE_STARTING_SERVER_MESSAGE,
                        Placeholder.parsed("amount", amount + ""),
                        Placeholder.parsed("group", serverGroup.getName())
                ));
                throwable.printStackTrace();
                return null;
            });
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof ServerGroupNotFoundException) {
                commandSource.sendMessage(PluginMessages.getInstance().getMessage(GROUP_NOT_FOUND_MESSAGE, Placeholder.parsed("name", group)));
                return null;
            }
            commandSource.sendMessage(PluginMessages.getInstance().getMessage(GROUP_LOAD_FAILED_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud server broadcast <server> <message>")
    @Permission("rexlcloud.server.broadcast")
    public void broadcastServer(CommandSource commandSource, @Argument(value = "server", suggestions = "server") String server, @Argument(value = "message") @Greedy String message) {
        RexlCloudApi.getInstance().getPlayers().thenAccept(players -> {
            Arrays.stream(players)
                    .filter(player -> player.getServer().getName().contains(server) || player.getProxyServer().getName().contains(server))
                    .forEach(player -> {
                        player.sendMessage(MessageParser.parse(message));
                    });
        });
    }

    @Command("rexlcloud|cloud server stop <server>")
    @Permission("rexlcloud.server.stop")
    public void stopServer(CommandSource commandSource, @Argument(value = "server", suggestions = "server") String server) {
        RexlCloudApi.getInstance().stopServer(server).thenAccept(response -> {
            commandSource.sendMessage(PluginMessages.getInstance().getMessage(STOP_SERVER_MESSAGE,
                    Placeholder.parsed("server", server)
            ));
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof ServerNotFoundException) {
                commandSource.sendMessage(PluginMessages.getInstance().getMessage(SERVER_NOT_FOUND_MESSAGE,
                        Placeholder.parsed("server", server)
                ));
                return null;
            }
            commandSource.sendMessage(PluginMessages.getInstance().getMessage(ERROR_WILE_STOPPING_SERVER_MESSAGE,
                    Placeholder.parsed("server", server)
            ));
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud server execute <server> <command>")
    @Permission("rexlcloud.server.execute")
    public void executeServer(CommandSource commandSource, @Argument(value = "server", suggestions = "server") String serverName, @Argument("command") @Greedy String command) {
        RexlCloudApi.getInstance().getServer(serverName).thenAccept(server -> {
            RexlCloudApi.getInstance().executeOnServer(server, command).thenAccept(response -> {
                commandSource.sendMessage(PluginMessages.getInstance().getMessage(SERVER_EXECUTE_COMMAND_MESSAGE,
                        Placeholder.parsed("server", server.getName()),
                        Placeholder.parsed("command", command)
                ));
            }).exceptionally(throwable -> {
                commandSource.sendMessage(PluginMessages.getInstance().getMessage(ERROR_WILE_SERVER_EXECUTING_COMMAND_MESSAGE,
                        Placeholder.parsed("server", server.getName()),
                        Placeholder.parsed("command", command)
                ));
                throwable.printStackTrace();
                return null;
            });
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof ServerNotFoundException) {
                commandSource.sendMessage(PluginMessages.getInstance().getMessage(SERVER_NOT_FOUND_MESSAGE,
                        Placeholder.parsed("server", serverName)
                ));
                return null;
            }
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud server list")
    @Permission("rexlcloud.server.list")
    public void listAllServer(CommandSource commandSource) {
        RexlCloudApi.getInstance().getAllServers().thenAccept(servers -> {
            if (servers.length == 0) {
                commandSource.sendMessage(Component.text("§4No servers found!"));
                return;
            }
            var nl = servers.length - 1;
            for (var server : servers) {
                commandSource.sendMessage(PluginMessages.getInstance().getMessage(SERVER_LIST_MESSAGE,
                        Placeholder.parsed("name", server.getName()),
                        Placeholder.parsed("group", server.getServerGroup().getName()),
                        Placeholder.parsed("wrapper", server.getWrapper().getName()),
                        Placeholder.parsed("port", server.getPort() + ""),
                        Placeholder.parsed("state", server.getState())
                ));
                if (nl > 0) {
                    commandSource.sendMessage(Component.empty());
                    nl--;
                }
            }
        }).exceptionally(throwable -> {
            commandSource.sendMessage(PluginMessages.getInstance().getMessage(ERROR_WILE_LOADING_SERVER_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Suggestions("server")
    public List<String> serverSuggestion(CommandContext<CommandSource> sender, String input) {
        return RexlCloudApi.getInstance().getAllServerNames().stream().filter(s -> s.startsWith(input)).toList();
    }

}
