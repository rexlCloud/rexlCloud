package de.rexlcloud.core.network;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class PacketContainer implements Serializable {

    private PacketType packetType;
    private Object packet;

}
