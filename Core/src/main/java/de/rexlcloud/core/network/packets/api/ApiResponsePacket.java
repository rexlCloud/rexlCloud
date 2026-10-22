package de.rexlcloud.core.network.packets.api;

import de.rexlcloud.core.network.packets.types.ServerToMasterPacket;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class ApiResponsePacket implements Serializable, ServerToMasterPacket {

    private int requestId;
    private String server;
    private Object response;

}
