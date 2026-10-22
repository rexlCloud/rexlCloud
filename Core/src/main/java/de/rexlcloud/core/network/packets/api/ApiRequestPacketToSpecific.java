package de.rexlcloud.core.network.packets.api;

import de.rexlcloud.core.network.packets.types.WrapperToSpecific;
import lombok.Getter;

@Getter
public class ApiRequestPacketToSpecific<T> extends ApiRequestPacket<T> implements WrapperToSpecific {
    private final String targetServer;

    public ApiRequestPacketToSpecific(int requestId, String server, T[] data, String targetServer) {
        super(requestId, server, data);
        this.targetServer = targetServer;
    }

    public ApiRequestPacketToSpecific(ApiRequestPacket<T> packet, String targetServer) {
        super(packet.getRequestId(), packet.getServer(), packet.getData());
        this.targetServer = targetServer;
    }

    @Override
    public String getTargetServerName() {
        return targetServer;
    }
}
