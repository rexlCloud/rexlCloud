package de.rexlcloud.proxy.bungee.commands;

import de.rexlcloud.proxy.bungee.BungeeText;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotation.specifier.Range;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.api.exceptions.ServerGroupNotFoundException;
import de.rexlcloud.core.api.exceptions.ServerNotFoundException;
import de.rexlcloud.proxy.bungee.BungeeProxy;
import de.rexlcloud.proxy.common.config.PluginMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.md_5.bungee.api.CommandSender;

import java.util.List;

import static de.rexlcloud.core.config.MessageKey.*;

public class CloudCommandsServer {

    @Command("rexlcloud|cloud server start <group> <amount>")
    @Permission("rexlcloud.server.start")
    public void startServer(CommandSender sender, @Argument("group") String group, @Argument(value = "amount") @Range(min = "1") int amount) {
        RexlCloudApi.getInstance().getServerGroup(group).thenAccept(serverGroup -> {
            RexlCloudApi.getInstance().startServer(serverGroup, amount).thenAccept(response -> {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(START_SERVER_MESSAGE,
                        Placeholder.parsed("amount", amount + ""),
                        Placeholder.parsed("group", serverGroup.getName())
                ));
            }).exceptionally(throwable -> {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(ERROR_WILE_STARTING_SERVER_MESSAGE,
                        Placeholder.parsed("amount", amount + ""),
                        Placeholder.parsed("group", serverGroup.getName())
                ));
                throwable.printStackTrace();
                return null;
            });
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof ServerGroupNotFoundException) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(GROUP_NOT_FOUND_MESSAGE, Placeholder.parsed("name", group)));
                return null;
            }
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(GROUP_LOAD_FAILED_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud server stop <server>")
    @Permission("rexlcloud.server.stop")
    public void stopServer(CommandSender sender, @Argument(value = "server", suggestions = "server") String server) {
        RexlCloudApi.getInstance().stopServer(server).thenAccept(response -> {
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(STOP_SERVER_MESSAGE,
                    Placeholder.parsed("server", server)
            ));
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof ServerNotFoundException) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(SERVER_NOT_FOUND_MESSAGE,
                        Placeholder.parsed("server", server)
                ));
                return null;
            }
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(ERROR_WILE_STOPPING_SERVER_MESSAGE,
                    Placeholder.parsed("server", server)
            ));
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud server execute <server> <command>")
    @Permission("rexlcloud.server.execute")
    public void executeServer(CommandSender sender, @Argument(value = "server", suggestions = "server") String serverName, @Argument("command") @Greedy String command) {
        RexlCloudApi.getInstance().getServer(serverName).thenAccept(server -> {
            RexlCloudApi.getInstance().executeOnServer(server, command).thenAccept(response -> {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(SERVER_EXECUTE_COMMAND_MESSAGE,
                        Placeholder.parsed("server", server.getName()),
                        Placeholder.parsed("command", command)
                ));
            }).exceptionally(throwable -> {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(ERROR_WILE_SERVER_EXECUTING_COMMAND_MESSAGE,
                        Placeholder.parsed("server", server.getName()),
                        Placeholder.parsed("command", command)
                ));
                throwable.printStackTrace();
                return null;
            });
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof ServerNotFoundException) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(SERVER_NOT_FOUND_MESSAGE,
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
    public void listAllServer(CommandSender sender) {
        RexlCloudApi.getInstance().getAllServers().thenAccept(servers -> {
            if (servers.length == 0) {
                BungeeText.send(sender, Component.text("§4No servers found!"));
                return;
            }
            var nl = servers.length - 1;
            for (var server : servers) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(SERVER_LIST_MESSAGE,
                        Placeholder.parsed("name", server.getName()),
                        Placeholder.parsed("group", server.getServerGroup().getName()),
                        Placeholder.parsed("wrapper", server.getWrapper().getName()),
                        Placeholder.parsed("port", server.getPort() + ""),
                        Placeholder.parsed("state", server.getState() == null ? "" : server.getState())
                ));
                if (nl > 0) {
                    BungeeText.send(sender, Component.empty());
                    nl--;
                }
            }
        }).exceptionally(throwable -> {
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(ERROR_WILE_LOADING_SERVER_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Suggestions("server")
    public List<String> serverSuggestion(CommandContext<CommandSender> sender, String input) {
        return RexlCloudApi.getInstance().getAllServerNames().stream().filter(s -> s.startsWith(input)).toList();
    }

}
