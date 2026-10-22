package de.rexlcloud.bridge.listener;

import de.rexlcloud.bridge.helper.RexlCloudHelper;
import de.rexlcloud.core.network.packets.PlayerTeleportPacket;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;

public class PlayerJoinListener implements Listener {

    @Getter(AccessLevel.PUBLIC)
    private static final ArrayList<PlayerTeleportPacket> bufferedPlayerTeleports = new ArrayList<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public final void on(PlayerJoinEvent event) {
        var player = event.getPlayer();
        new ArrayList<>(bufferedPlayerTeleports).stream()
                .filter(playerTeleportPacket -> playerTeleportPacket.getUuid().equals(player.getUniqueId()))
                .findAny().ifPresent(playerTeleportPacket -> {
                    if (playerTeleportPacket.getBufferTime() >= System.currentTimeMillis()) {
                        var location = RexlCloudHelper.serverLocationToLocation(player.getWorld(), playerTeleportPacket.getServerLocation());
                        player.teleport(location);
                    }
                    bufferedPlayerTeleports.remove(playerTeleportPacket);
                });
    }

}
