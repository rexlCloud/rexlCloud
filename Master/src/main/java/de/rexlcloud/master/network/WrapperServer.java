package de.rexlcloud.master.network;

import de.rexlcloud.core.network.codec.PacketPipeline;
import de.rexlcloud.core.console.Console;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
public class WrapperServer implements Runnable {

    private String host;
    private int port;
    /**
     * Encrypts the connections; wrappers check the certificate's fingerprint.
     */
    private SslContext sslContext;

    @Override
    public void run() {
        Console.info("Starting the wrapper server!");
        var loopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        var loopGroup2 = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            var serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(loopGroup, loopGroup2)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(@NotNull SocketChannel socketChannel) {
                            // Length-prefixed JSON frames instead of Java serialization
                            socketChannel.pipeline().addLast("tls", sslContext.newHandler(socketChannel.alloc()));
                            PacketPipeline.configure(socketChannel.pipeline());
                            socketChannel.pipeline().addLast(new WrapperHandler());
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            cause.printStackTrace();
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, Integer.MAX_VALUE);
            var channelFuture = serverBootstrap.bind(host, port).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (Exception exc) {
            Console.error("The wrapper server could not be started!");
            exc.printStackTrace();
        }
    }
}
