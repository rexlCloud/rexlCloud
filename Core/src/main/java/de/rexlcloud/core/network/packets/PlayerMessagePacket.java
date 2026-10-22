package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.WrapperToSpecific;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class PlayerMessagePacket implements WrapperToSpecific {

    private UUID uuid;
    private String targetServerName;
    private String message;

    @Override
    public String getTargetServerName() {
        return targetServerName;
    }
}
