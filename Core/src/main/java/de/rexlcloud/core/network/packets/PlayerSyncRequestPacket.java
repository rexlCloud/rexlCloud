package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.WrapperToSpecific;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Asks a proxy to report all its players again (as {@link PlayerCloudJoinPacket}s), after the master restarted and
 * lost track of them.
 */
@Getter
@AllArgsConstructor
public class PlayerSyncRequestPacket implements WrapperToSpecific {

    private String targetServerName;

}
