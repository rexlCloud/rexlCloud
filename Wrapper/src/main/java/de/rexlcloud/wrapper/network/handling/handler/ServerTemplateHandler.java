package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.TemplateFilePacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import io.netty.channel.ChannelHandlerContext;

/**
 * A chunk of a template file from the master.
 */
public class ServerTemplateHandler implements IPacketHandler<TemplateFilePacket> {

    @Override
    public PacketType getType() {
        return PacketType.SERVER_TEMPLATE;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, TemplateFilePacket packet) {
        RexlCloudWrapper.getInstance().getTemplateStore().receive(packet);
    }

}
