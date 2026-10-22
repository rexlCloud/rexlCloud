package de.rexlcloud.proxy.velocity.commands;

import org.incendo.cloud.annotations.Command;
import com.velocitypowered.api.proxy.Player;
import de.rexlcloud.core.api.RexlCloudApi;
import de.rexlcloud.proxy.velocity.VelocityProxy;

public class LobbyCommands {

    @Command(value = "hub|lobby|l|leave", requiredSender = Player.class)
    public void hubCommand(Player sender) {
        RexlCloudApi.getInstance().getPlayer(sender.getUniqueId()).thenAccept(player ->
                VelocityProxy.getInstance().getLogin().getFallbackServer().ifPresent(server -> player.connect(server.getServerInfo().getName())));
    }


}
