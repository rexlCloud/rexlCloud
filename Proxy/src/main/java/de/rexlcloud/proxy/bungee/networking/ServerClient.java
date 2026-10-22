package de.rexlcloud.proxy.bungee.networking;

import de.rexlcloud.core.network.codec.PacketPipeline;
import de.rexlcloud.proxy.bungee.BungeeProxy;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;


@Getter
public class ServerClient implements Runnable {

    /**
     * Default port of the wrapper, for cloud.json files that do not name one.
     */
    public static final int DEFAULT_PORT = 2243;

    private final int port;

    private Channel channel;

    public ServerClient(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        // One thread is plenty for the single connection to the wrapper
        var eventLoopGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            var bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            // Length-prefixed JSON frames instead of Java serialization
                            PacketPipeline.configure(socketChannel.pipeline());
                            socketChannel.pipeline().addLast(new ServerHandler());
                        }
                    });
            var channelFuture = bootstrap.connect("127.0.0.1", port);
            channel = channelFuture.channel();
            var closeFuture = channel.closeFuture();
            // No reconnect: when the wrapper goes away the server shuts down (see ServerHandler), because the wrapper
            // stopped its servers or is gone
            closeFuture.sync();
        } catch (Exception exc) {
            exc.printStackTrace();
        } finally {
            eventLoopGroup.shutdownGracefully();
        }
    }
}
