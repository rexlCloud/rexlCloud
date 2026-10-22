package de.rexlcloud.proxy.bungee.commands;

import de.rexlcloud.proxy.bungee.BungeeText;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.api.exceptions.WrapperNotFoundException;
import de.rexlcloud.proxy.bungee.BungeeProxy;
import de.rexlcloud.proxy.common.config.PluginMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.md_5.bungee.api.CommandSender;

import java.util.List;

import static de.rexlcloud.core.config.MessageKey.*;

public class CloudCommandsWrapper {

    @Command("rexlcloud|cloud wrapper list")
    @Permission("rexlcloud.wrapper.list")
    public void listAllWrapper(CommandSender sender) {
        RexlCloudApi.getInstance().getWrappers().thenAccept(wrappers -> {
            if (wrappers.length == 0) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(NO_WRAPPER_FOUND_MESSAGE));
                return;
            }
            var nl = wrappers.length - 1;
            for (var wrapper : wrappers) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(WRAPPER_LIST_MESSAGE,
                        Placeholder.parsed("name", wrapper.getName()),
                        Placeholder.parsed("online", wrapper.isOnline() ? "<green>true<green>" : "<red>false</red>"),
                        Placeholder.parsed("cpu", wrapper.getCpuUsagePercent()),
                        Placeholder.parsed("used_memory", wrapper.getUsedMemoryString() + " GB"),
                        Placeholder.parsed("free_memory", wrapper.getFreeMemoryString() + " GB"),
                        Placeholder.parsed("total_memory", wrapper.getTotalMemoryString() + " GB")
                ));
                if (nl > 0) {
                    BungeeText.send(sender, Component.empty());
                    nl--;
                }
            }
        }).exceptionally(throwable -> {
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(NO_WRAPPER_FOUND_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud wrapper info <wrapper>")
    @Permission("rexlcloud.wrapper.info")
    public void wrapperInfo(CommandSender sender, @Argument(value = "wrapper", suggestions = "wrapper") String wrapperName) {
        RexlCloudApi.getInstance().getWrapper(wrapperName).thenAccept(wrapper -> {
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(WRAPPER_INFO_MESSAGE,
                    Placeholder.parsed("name", wrapper.getName()),
                    Placeholder.parsed("online", wrapper.isOnline() ? "<green>true<green>" : "<red>false</red>"),
                    Placeholder.parsed("cpu", wrapper.getCpuUsagePercent()),
                    Placeholder.parsed("used_memory", wrapper.getUsedMemoryString() + " GB"),
                    Placeholder.parsed("free_memory", wrapper.getFreeMemoryString() + " GB"),
                    Placeholder.parsed("total_memory", wrapper.getTotalMemoryString() + " GB")
            ));
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof WrapperNotFoundException) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(NO_WRAPPER_FOUND_MESSAGE));
                return null;
            }
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(NO_WRAPPER_FOUND_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud wrapper stop <wrapper>")
    @Permission("rexlcloud.wrapper.stop")
    public void stopWrapper(CommandSender sender, @Argument(value = "wrapper", suggestions = "wrapper") String wrapperName) {
        RexlCloudApi.getInstance().getWrapper(wrapperName).thenAccept(wrapper -> {
            RexlCloudApi.getInstance().stopWrapper(wrapper).thenAccept(response -> {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(STOP_WRAPPER_MESSAGE,
                        Placeholder.parsed("wrapper", wrapper.getName())
                ));
            }).exceptionally(throwable -> {
                if (throwable.getCause() instanceof WrapperNotFoundException) {
                    BungeeText.send(sender, PluginMessages.getInstance().getMessage(NO_WRAPPER_FOUND_MESSAGE));
                    return null;
                }
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(NO_WRAPPER_FOUND_MESSAGE));
                throwable.printStackTrace();
                return null;
            });
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof WrapperNotFoundException) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(NO_WRAPPER_FOUND_MESSAGE));
                return null;
            }
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(NO_WRAPPER_FOUND_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Suggestions("wrapper")
    public List<String> wrapperSuggestion(CommandContext<CommandSender> sender, String input) {
        return RexlCloudApi.getInstance().getAllWrapperNames().stream().filter(s -> s.startsWith(input)).toList();
    }

}
