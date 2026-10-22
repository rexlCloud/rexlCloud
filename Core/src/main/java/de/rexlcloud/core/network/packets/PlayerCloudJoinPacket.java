package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.ServerToMasterPacket;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class PlayerCloudJoinPacket implements ServerToMasterPacket {

    private String name;
    private UUID uuid;
    private long login;
    private String serverName;
    private String proxyServerName;

}
