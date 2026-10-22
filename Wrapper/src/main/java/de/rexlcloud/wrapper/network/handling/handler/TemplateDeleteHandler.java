package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.TemplateDeletePacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import io.netty.channel.ChannelHandlerContext;

/**
 * Removes template files or templates the master no longer has.
 */
public class TemplateDeleteHandler implements IPacketHandler<TemplateDeletePacket> {

    @Override
    public PacketType getType() {
        return PacketType.TEMPLATE_DELETE;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, TemplateDeletePacket packet) {
        RexlCloudWrapper.getInstance().getTemplateStore().delete(packet);
    }

}
