package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.core.server.ProxyForwarding;
import de.rexlcloud.core.server.ServerGroup;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ServerStartPacket implements Packet {

    private String name;
    private ServerGroup serverGroup;
    private int port;
    private UUID key;
    private boolean maintenance;
    private ProxyForwarding forwarding;

}
