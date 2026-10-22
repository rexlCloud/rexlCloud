package de.rexlcloud.core.network.packets.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class ApiUpdateObjectPacket implements Serializable {

    private String targetServer;
    private String name;
    private Object object;

}
