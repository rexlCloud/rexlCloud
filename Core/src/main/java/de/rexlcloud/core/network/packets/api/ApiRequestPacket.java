package de.rexlcloud.core.network.packets.api;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class ApiRequestPacket<T> implements Packet {

    private int requestId;
    /**
     * The requesting server; set by the wrapper from the connection, whatever the plugin sent.
     */
    @Setter
    private String server;
    private T[] data;

}
