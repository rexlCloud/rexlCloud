package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.ServerToMasterPacket;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class PlayerProxyDisconnectPacket implements ServerToMasterPacket {

    private UUID uuid;

}
