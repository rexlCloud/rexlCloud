package de.rexlcloud.proxy.velocity.commands;

import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import com.velocitypowered.api.command.CommandSource;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.api.exceptions.ServerGroupNotFoundException;
import de.rexlcloud.proxy.common.config.PluginMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import static de.rexlcloud.core.config.MessageKey.*;

public class CloudCommandsGroup {

    @Command("rexlcloud|cloud group list")
    @Permission("rexlcloud.group.list")
    public void listAllGroups(CommandSource commandSource) {
        RexlCloudApi.getInstance().getServerGroups().thenAccept(serverGroups -> {
            if (serverGroups.length == 0) {
                return;
            }
            var nl = serverGroups.length - 1;
            for (var serverGroup : serverGroups) {
                commandSource.sendMessage(PluginMessages.getInstance().getMessage(GROUP_LIST_MESSAGE,
                        Placeholder.parsed("name", serverGroup.getName()),
                        Placeholder.parsed("wrapper", serverGroup.getWrapper() == null ? "none" : serverGroup.getWrapper()),
                        Placeholder.parsed("type", serverGroup.getType().name()),
                        Placeholder.parsed("min_servers", serverGroup.getMinServers() + ""),
                        Placeholder.parsed("memory", serverGroup.getMemory() + "MB"),
                        Placeholder.parsed("java_args", serverGroup.getJavaArguments())
                ));
                if (nl > 0) {
                    commandSource.sendMessage(Component.empty());
                    nl--;
                }
            }
        }).exceptionally(throwable -> {
            commandSource.sendMessage(PluginMessages.getInstance().getMessage(GROUP_LOAD_FAILED_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud group info <group>")
    @Permission("rexlcloud.group.info")
    public void groupInfo(CommandSource commandSource, @Argument("group") String group) {
        RexlCloudApi.getInstance().getServerGroup(group).thenAccept(serverGroup -> {
            if (serverGroup == null) {
                commandSource.sendMessage(PluginMessages.getInstance().getMessage(GROUP_NOT_FOUND_MESSAGE, Placeholder.parsed("name", group)));
                return;
            }
            commandSource.sendMessage(PluginMessages.getInstance().getMessage(GROUP_INFO_MESSAGE,
                    Placeholder.parsed("name", serverGroup.getName()),
                    Placeholder.parsed("wrapper", serverGroup.getWrapper() == null ? "none" : serverGroup.getWrapper()),
                    Placeholder.parsed("type", serverGroup.getType().name()),
                    Placeholder.parsed("min_servers", serverGroup.getMinServers() + ""),
                    Placeholder.parsed("memory", serverGroup.getMemory() + "MB"),
                    Placeholder.parsed("java_args", serverGroup.getJavaArguments())
            ));
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

}
