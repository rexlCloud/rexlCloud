package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class WrapperConnectPacket implements Packet {

    private UUID authKey;
    /**
     * Servers the wrapper is running, e.g. because they kept running while the master restarted; the master takes
     * them over instead of starting new ones. {@code null} from older wrappers.
     */
    private List<RunningServer> servers;

    /**
     * A server that is running on the wrapper.
     */
    @Getter
    @AllArgsConstructor
    public static class RunningServer {
        private String name;
        private UUID key;
        private String group;
        private int port;
        private boolean online;
    }

}
