package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.WrapperToProxyPacket;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProxyAddServerPacket implements WrapperToProxyPacket {

    private String name;
    private String address;
    private int port;
    boolean isFallback;

}
