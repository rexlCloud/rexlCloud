package de.rexlcloud.bridge.networking;

import de.rexlcloud.core.network.codec.PacketPipeline;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * The connection to the wrapper on 127.0.0.1. When it ends, the server shuts down (see {@link ServerHandler}).
 */
public final class ServerClient {

    private ServerClient() {
    }

    public static void start(int port) {
        var eventLoopGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        // Length-prefixed JSON frames, as between master and wrappers
                        PacketPipeline.configure(socketChannel.pipeline());
                        socketChannel.pipeline().addLast(new ServerHandler());
                    }
                })
                .connect("127.0.0.1", port)
                .channel().closeFuture().addListener(closed -> eventLoopGroup.shutdownGracefully());
    }

}
