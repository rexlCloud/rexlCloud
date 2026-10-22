package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ServerExecuteCommandPacket implements Packet {

    private String serverName;
    private String command;

}
