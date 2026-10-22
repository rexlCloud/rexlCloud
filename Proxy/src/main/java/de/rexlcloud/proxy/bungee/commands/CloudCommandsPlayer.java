package de.rexlcloud.proxy.bungee.commands;

import de.rexlcloud.proxy.bungee.BungeeText;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import de.rexlcloud.core.api.Player;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.core.api.exceptions.PlayerNotFoundException;
import de.rexlcloud.proxy.bungee.BungeeProxy;
import de.rexlcloud.proxy.common.config.PluginMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.md_5.bungee.api.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static de.rexlcloud.core.config.MessageKey.*;

public class CloudCommandsPlayer {

    private static final SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    @Command("rexlcloud|cloud players list")
    @Permission("rexlcloud.players.list")
    public void listAllPlayers(CommandSender sender) {
        RexlCloudApi.getInstance().getPlayers().thenAccept(players -> {
            if (players.length == 0) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(NO_PLAYERS_ONLINE_MESSAGE));
                return;
            }
            var nl = players.length - 1;
            for (var player : players) {
                BungeeText.send(sender, PluginMessages.getInstance().getMessage(PLAYER_LIST_MESSAGE,
                        Placeholder.parsed("name", player.getName()),
                        Placeholder.parsed("uuid", player.getUuid().toString()),
                        Placeholder.parsed("server", player.getServer().getName()),
                        Placeholder.parsed("login", format.format(new Date(player.getLogin())))
                ));
                if (nl > 0) {
                    BungeeText.send(sender, Component.empty());
                    nl--;
                }
            }
        }).exceptionally(throwable -> {
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(PLAYER_LOAD_FAILED_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud players info <player>")
    @Permission("rexlcloud.players.info")
    public void playerInfo(CommandSender sender, @Argument(value = "player", suggestions = "player") String playerName) {
        RexlCloudApi.getInstance().getPlayer(playerName).thenAccept(player -> {
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(PLAYER_INFO_MESSAGE,
                    Placeholder.parsed("name", player.getName()),
                    Placeholder.parsed("uuid", player.getUuid().toString()),
                    Placeholder.parsed("server", player.getServer().getName()),
                    Placeholder.parsed("login", format.format(new Date(player.getLogin())))
            ));
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PlayerNotFoundException) {
                BungeeText.send(sender, 
                        PluginMessages.getInstance().getMessage(PLAYER_NOT_FOUND, Placeholder.parsed("name", playerName)));
                return null;
            }
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(PLAYER_LOAD_FAILED_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Command("rexlcloud|cloud players kick <player> <message>")
    @Permission("rexlcloud.players.kick")
    public void kickPlayer(CommandSender sender, @Argument(value = "player", suggestions = "player") String playerName,
                           @Argument(value = "message") @Greedy String message) {
        RexlCloudApi.getInstance().getPlayer(playerName).thenAccept(player -> {
            player.kick(message);
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof PlayerNotFoundException) {
                BungeeText.send(sender, 
                        PluginMessages.getInstance().getMessage(PLAYER_NOT_FOUND, Placeholder.parsed("name", playerName)));
                return null;
            }
            BungeeText.send(sender, PluginMessages.getInstance().getMessage(PLAYER_LOAD_FAILED_MESSAGE));
            throwable.printStackTrace();
            return null;
        });
    }

    @Suggestions("player")
    public List<String> playerSuggestion(CommandContext<CommandSender> sender, String input) {
        return RexlCloudApi.getInstance().getCachedPlayers().stream().map(Player::getName).filter(s -> s.startsWith(input)).toList();
    }

}
