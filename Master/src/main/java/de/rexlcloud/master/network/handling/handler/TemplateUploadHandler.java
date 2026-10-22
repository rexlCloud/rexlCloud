package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.TemplateUploadPacket;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.network.handling.WrapperPacketHandler;
import de.rexlcloud.master.wrapper.Wrapper;
import io.netty.channel.ChannelHandlerContext;

/**
 * A chunk of a server's files that are saved as a template.
 */
public class TemplateUploadHandler implements WrapperPacketHandler<TemplateUploadPacket> {

    @Override
    public PacketType getType() {
        return PacketType.TEMPLATE_UPLOAD;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, TemplateUploadPacket packet) {
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, Wrapper wrapper, TemplateUploadPacket packet) {
        RexlCloudMaster.getInstance().getTemplateUploads().receive(wrapper, packet);
    }
}
