package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ServerConsolePacket implements Packet {

    private String serverName;
    private String message;
    private long messageTime;

}
