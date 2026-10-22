package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The first packet of a server's cloud plugin to its wrapper.
 */
@Getter
@AllArgsConstructor
public class ServerOnlinePacket implements Packet {

    private String name;
    /**
     * The secret the wrapper wrote into the server's cloud.json for this run. Unlike the server key, which every
     * plugin sees through the API, only the server itself knows it.
     */
    private String token;

}
