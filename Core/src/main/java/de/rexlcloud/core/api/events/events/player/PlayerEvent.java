package de.rexlcloud.core.api.events.events.player;

import de.rexlcloud.core.api.Player;
import de.rexlcloud.core.api.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * An event about a player.
 */
@Getter
@AllArgsConstructor
public abstract class PlayerEvent extends Event {

    /**
     * The player.
     */
    @NonNull
    private final Player player;

}
