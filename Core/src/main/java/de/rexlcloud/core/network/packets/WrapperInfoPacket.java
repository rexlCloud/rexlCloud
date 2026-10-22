package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.core.server.runtime.JavaRuntime;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class WrapperInfoPacket implements Packet {

    private double cpuUsage;
    private long usedMemory, freeMemory, totalMemory;
    /**
     * Java installations of the wrapper machine.
     */
    private List<JavaRuntime> javaRuntimes;

}
