package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.TemplateStatePacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.WrapperPacketHandler;
import de.rexlcloud.master.wrapper.Wrapper;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;

/**
 * A wrapper reported its templates; the master sends what is missing.
 */
public class TemplateStateHandler implements WrapperPacketHandler<TemplateStatePacket> {

    @Override
    public PacketType getType() {
        return PacketType.TEMPLATE_STATE;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, TemplateStatePacket packet) {
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, Wrapper wrapper, TemplateStatePacket packet) {
        // Sending the files can take minutes; the wrapper's other packets (e.g. its heartbeat) must not wait
        Thread.ofVirtual().name("template-sync-" + wrapper.getName()).start(() ->
                RexlCloudMaster.getInstance().getTemplateSync().onState(wrapper,
                        packet.getTemplates() == null ? Map.of() : packet.getTemplates()));
    }
}
