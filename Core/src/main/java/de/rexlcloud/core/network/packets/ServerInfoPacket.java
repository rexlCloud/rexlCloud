package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ServerInfoPacket implements Packet {

    private String serverName;
    private boolean isOnline;
    private String state;

}
