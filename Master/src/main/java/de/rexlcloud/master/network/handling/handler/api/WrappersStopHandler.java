package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.exceptions.WrapperNotFoundException;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

public class WrappersStopHandler implements ApiPacketHandler<String> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_WRAPPER_STOP;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<String> packet) {
        var wrapperManager = RexlCloudMaster.getInstance().getWrapperManager();
        var wrapper = wrapperManager.getWrapperByName(packet.getData()[0]);
        if (wrapper == null) {
            response(packet, new WrapperNotFoundException(packet.getData()[0]));
            return;
        }
        wrapperManager.stopWrapper(wrapper);
        response(packet, true);
    }
}
