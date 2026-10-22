package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.WrapperToProxyPacket;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProxyRemoveServerPacket implements WrapperToProxyPacket {

    private String name;

}
