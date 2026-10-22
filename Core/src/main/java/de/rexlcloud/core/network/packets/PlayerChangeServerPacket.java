package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.ServerToMasterPacket;
import de.rexlcloud.core.network.packets.types.WrapperToProxyPacket;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class PlayerChangeServerPacket implements ServerToMasterPacket, WrapperToProxyPacket {

    private UUID uuid;
    private String serverName;

}
