package de.rexlcloud.proxy.bungee.commands;

import de.rexlcloud.proxy.bungee.BungeeText;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.api.exceptions.ServerGroupNotFoundException;
import de.rexlcloud.proxy.bungee.BungeeProxy;
import de.rexlcloud.proxy.common.config.PluginMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.md_5.bungee.api.CommandSender;

import static de.rexlcloud.core.config.MessageKey.*;

public class CloudCommandsGroup {

    @Command("rexlcloud|cloud group list")
    @Permission("rexlcloud.group.list")
    public void listAllGroups(CommandSender sender) {
        RexlCloudApi.getInstance().getServerGroups().thenAccept(serverGroups -> {
            if (serverGroups.length == 0) {
                return;
            }
            var nl = serverGroups.length - 1;
            for (var serverGroup : serverGroups) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(GROUP_LIST_MESSAGE,
                                Placeholder.parsed("name", serverGroup.getName()),
                                Placeholder.parsed("wrapper", serverGroup.getWrapper() == null ? "none" : serverGroup.getWrapper()),
                                Placeholder.parsed("type", serverGroup.getType().name()),
                                Placeholder.parsed("min_servers", serverGroup.getMinServers() + ""),
                                Placeholder.parsed("memory", serverGroup.getMemory() + "MB"),
                                Placeholder.parsed("java_args", serverGroup.getJavaArguments())
                        ));
                if (nl > 0) {
                    BungeeText.send(sender, Component.empty());
                    nl--;
                }
            }
        }).exceptionally(throwable -> {
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(GROUP_LOAD_FAILED_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud group info <group>")
    @Permission("rexlcloud.group.info")
    public void groupInfo(CommandSender sender, @Argument("group") String group) {
        RexlCloudApi.getInstance().getServerGroup(group).thenAccept(serverGroup -> {
            if (serverGroup == null) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(GROUP_NOT_FOUND_MESSAGE, Placeholder.parsed("name", group)));
                return;
            }
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(GROUP_INFO_MESSAGE,
                    Placeholder.parsed("name", serverGroup.getName()),
                    Placeholder.parsed("wrapper", serverGroup.getWrapper() == null ? "none" : serverGroup.getWrapper()),
                    Placeholder.parsed("type", serverGroup.getType().name()),
                    Placeholder.parsed("min_servers", serverGroup.getMinServers() + ""),
                    Placeholder.parsed("memory", serverGroup.getMemory() + "MB"),
                    Placeholder.parsed("java_args", serverGroup.getJavaArguments())
            ));
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

}
