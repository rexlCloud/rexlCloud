package de.rexlcloud.master.network.handling.handler.api;

import de.rexlcloud.core.api.Wrapper;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.ApiPacketHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;

public class GetWrappersHandler implements ApiPacketHandler<Object> {
    @Override
    public PacketType getType() {
        return PacketType.API_REQUEST_WRAPPERS;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ApiRequestPacket<Object> packet) {
        var wrapperManager = RexlCloudMaster.getInstance().getWrapperManager();
        ArrayList<Wrapper> wrappers = new ArrayList<>();
        wrapperManager.getWrapperRegistry().forEach((s, credentials) -> {
            var wrapperByName = wrapperManager.getWrapperByName(s);
            if (wrapperByName != null) {
                wrappers.add(wrapperByName.getApiWrapper());
            }
        });
        response(packet, wrappers.toArray(Wrapper[]::new));
    }
}
