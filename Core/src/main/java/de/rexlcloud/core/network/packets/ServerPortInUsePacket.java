package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Wrapper to master: the server could not start because something else on the wrapper uses its port.
 */
@Getter
@AllArgsConstructor
public class ServerPortInUsePacket implements Packet {

    private String name;
    private UUID key;
    private int port;

}
