package de.rexlcloud.core.api.events.events;

import de.rexlcloud.core.api.events.Event;
import de.rexlcloud.core.network.PacketContainer;
import lombok.Getter;
import lombok.Setter;

/**
 * A packet from a wrapper reached the master. Cancelling it stops the master from handling it; meant for
 * modules that extend the protocol.
 */
@Getter
public class PacketReceivedEvent extends Event {

    /**
     * Whether the master ignores the packet.
     */
    @Setter
    private boolean canceled;
    /**
     * The packet.
     */
    private final PacketContainer packetContainer;

    public PacketReceivedEvent(PacketContainer packetContainer) {
        this.packetContainer = packetContainer;
    }



}
