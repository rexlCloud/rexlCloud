package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.api.ServerLocation;
import de.rexlcloud.core.network.packets.types.WrapperToSpecific;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class PlayerTeleportPacket implements WrapperToSpecific {

    private UUID uuid;
    @NonNull
    private String targetServerName;
    private ServerLocation serverLocation;
    @Setter
    private long bufferTime;

    @NonNull
    @Override
    public String getTargetServerName() {
        return targetServerName;
    }
}
