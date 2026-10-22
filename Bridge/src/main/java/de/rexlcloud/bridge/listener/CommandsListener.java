package de.rexlcloud.bridge.listener;

import de.rexlcloud.bridge.Bridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CommandsListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public final void on(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().equalsIgnoreCase("/restart") || event.getMessage().equalsIgnoreCase("/stop")) {
            event.setCancelled(true);
        }
        Bridge.getInstance().shutdownServer();
    }

}
