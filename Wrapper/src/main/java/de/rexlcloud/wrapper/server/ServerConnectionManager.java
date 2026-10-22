package de.rexlcloud.wrapper.server;

import de.rexlcloud.core.network.codec.PacketPipeline;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.Optional;

public class ServerConnectionManager implements Runnable {

    @Override
    public void run() {
        var loopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        var loopGroup2 = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            var serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(loopGroup, loopGroup2)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) {
                            // Length-prefixed JSON frames instead of Java serialization
                            PacketPipeline.configure(socketChannel.pipeline());
                            socketChannel.pipeline().addLast(new ServerHandle());
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            cause.printStackTrace();
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, Integer.MAX_VALUE);
            var channelFuture = serverBootstrap.bind("127.0.0.1", RexlCloudWrapper.getInstance().getWrapperConfig().getServerPort()).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }

    public Optional<Server> getServerFromChannel(Channel channel) {
        return RexlCloudWrapper.getInstance().getServerManager().getServers().stream().filter(server -> server.getChannel() != null).filter(server -> server.getChannel().equals(channel)).findFirst();
    }

}
