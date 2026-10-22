package de.rexlcloud.core.api.events.events.player;

import de.rexlcloud.core.api.Player;
import de.rexlcloud.core.api.Server;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player switched to another server.
 */
@Getter
public class PlayerServerChangeEvent extends PlayerEvent {

    /**
     * The server the player came from, {@code null} if unknown.
     */
    @Nullable
    private final Server from;
    /**
     * The server the player is on now.
     */
    private final Server to;

    public PlayerServerChangeEvent(@NonNull Player player, @Nullable Server from, @NonNull Server to) {
        super(player);
        this.from = from;
        this.to = to;
    }
}
