package de.rexlcloud.master.network.handling.handler;

import de.rexlcloud.core.api.events.events.wrapper.WrapperConnectedEvent;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.WrapperConnectPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.server.ServerAdoption;
import de.rexlcloud.master.wrapper.Wrapper;
import io.netty.channel.ChannelHandlerContext;

public class WrapperConnectHandler implements IPacketHandler<WrapperConnectPacket> {

    @Override
    public boolean requiresRegisteredWrapper() {
        return false;
    }

    @Override
    public PacketType getType() {
        return PacketType.WRAPPER_CONNECT;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, WrapperConnectPacket packet) {
        var address = ctx.channel().remoteAddress().toString().replace("/", "")
                .split(":")[0];
        var wrapperManager = RexlCloudMaster.getInstance().getWrapperManager();
        if (!wrapperManager.canWrapperRegister(address, packet.getAuthKey())) {
            Console.warn("Refused a wrapper from %s: unknown key", address);
            ctx.channel().close();
            return;
        }
        var wrapper = new Wrapper(ctx.channel(), wrapperManager.getWrapperName(address, packet.getAuthKey()), address);
        wrapperManager.registerWrapper(wrapper);
        RexlCloudMaster.getInstance().getEventManager().callEvent(new WrapperConnectedEvent(wrapper.getApiWrapper()));
        Console.info("%s connected", wrapper.getName());
        ServerAdoption.adopt(wrapper, packet.getServers());
        wrapper.sendMessages();
        RexlCloudMaster.getInstance().getTemplateSync().onConnect(wrapper);
    }
}
