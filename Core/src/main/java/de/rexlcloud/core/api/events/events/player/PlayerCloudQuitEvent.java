package de.rexlcloud.core.api.events.events.player;

import de.rexlcloud.core.api.Player;
import lombok.NonNull;

/**
 * A player left the network.
 */
public class PlayerCloudQuitEvent extends PlayerEvent {

    public PlayerCloudQuitEvent(@NonNull Player player) {
        super(player);
    }
}
