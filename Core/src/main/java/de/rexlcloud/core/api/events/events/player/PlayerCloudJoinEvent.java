package de.rexlcloud.core.api.events.events.player;

import de.rexlcloud.core.api.Player;
import lombok.NonNull;

/**
 * A player joined the network. After a restart of the master it is also fired for the players the proxies
 * report again.
 */
public class PlayerCloudJoinEvent extends PlayerEvent {

    public PlayerCloudJoinEvent(@NonNull Player player) {
        super(player);
    }
}
