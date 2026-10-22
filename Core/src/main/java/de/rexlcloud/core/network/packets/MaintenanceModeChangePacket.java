package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.WrapperToProxyPacket;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public class MaintenanceModeChangePacket implements WrapperToProxyPacket {

    private boolean value;

}
