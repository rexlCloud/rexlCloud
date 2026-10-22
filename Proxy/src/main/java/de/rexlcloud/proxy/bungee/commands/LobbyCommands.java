package de.rexlcloud.proxy.bungee.commands;

import org.incendo.cloud.annotations.Command;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.proxy.bungee.BungeeProxy;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class LobbyCommands {

    @Command(value = "hub|lobby|l|leave", requiredSender = ProxiedPlayer.class)
    public void hubCommand(ProxiedPlayer sender) {
        RexlCloudApi.getInstance().getPlayer(sender.getUniqueId()).thenAccept(player ->
                BungeeProxy.getInstance().getLogin().getFallbackServer().ifPresent(server -> player.connect(server.getName())));
    }

}
