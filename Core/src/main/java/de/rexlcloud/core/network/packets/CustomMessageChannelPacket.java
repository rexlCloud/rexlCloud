package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.ServerToMasterPacket;
import de.rexlcloud.core.network.packets.types.WrapperToSpecific;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CustomMessageChannelPacket implements WrapperToSpecific, ServerToMasterPacket {

    private String targetServerName;
    private String sourceServerName;
    private String messageChannel;
    private byte[] objectBytes;

}
