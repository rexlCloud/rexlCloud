package de.rexlcloud.core.api.exceptions;

import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * No player with this name or UUID is online.
 */
public class PlayerNotFoundException extends CloudException {

    @Getter
    @Nullable
    private String playerName = null;
    @Getter
    @Nullable
    private UUID playerUUID = null;

    public PlayerNotFoundException(@NonNull String playerName) {
        super("Cant find Player with name: " + playerName);
        this.playerName = playerName;
    }

    public PlayerNotFoundException(@NonNull UUID playerUUID) {
        super("Cant find Player with uuid: " + playerUUID);
        this.playerUUID = playerUUID;
    }

}
