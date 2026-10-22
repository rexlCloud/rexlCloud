package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.config.PluginMessageConfig;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import io.netty.channel.ChannelHandlerContext;

public class ServerSyncMessagesHandler implements IPacketHandler<Packet> {
    @Override
    public PacketType getType() {
        return PacketType.SYNC_MESSAGES;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, Packet packet) {
        RexlCloudWrapper.getInstance().setPluginMessageConfig((PluginMessageConfig) packet);
        var container = new PacketContainer(PacketType.SYNC_MESSAGES, packet);
        RexlCloudWrapper.getInstance().getServerManager().sendPacketToAllServers(container);
    }

}
