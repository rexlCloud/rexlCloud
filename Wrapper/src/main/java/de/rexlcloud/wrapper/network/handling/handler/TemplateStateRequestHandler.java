package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.TemplateStatePacket;
import de.rexlcloud.core.network.packets.TemplateStateRequestPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;

/**
 * Reports the templates of this wrapper, so the master can send what is missing.
 */
public class TemplateStateRequestHandler implements IPacketHandler<TemplateStateRequestPacket> {

    @Override
    public PacketType getType() {
        return PacketType.TEMPLATE_STATE_REQUEST;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, TemplateStateRequestPacket packet) {
        try {
            var state = RexlCloudWrapper.getInstance().getTemplateStore().state();
            ctx.channel().writeAndFlush(new PacketContainer(PacketType.TEMPLATE_STATE, new TemplateStatePacket(state)));
        } catch (IOException e) {
            Console.error("The templates could not be read: %s", e.getMessage());
        }
    }

}
