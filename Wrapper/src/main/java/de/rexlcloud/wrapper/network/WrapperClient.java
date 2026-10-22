package de.rexlcloud.wrapper.network;

import de.rexlcloud.core.network.codec.PacketPipeline;
import de.rexlcloud.core.console.Console;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import lombok.Getter;

import java.util.concurrent.TimeUnit;

/**
 * Connection to the master. Reconnects on its own, waiting longer after each failed attempt.
 */
public class WrapperClient implements Runnable {

    private static final int MIN_RETRY_SECONDS = 5;
    // Short, so a restarted master gets its wrappers (and their running servers) back quickly
    private static final int MAX_RETRY_SECONDS = 15;
    /**
     * While retrying at the longest delay, only every n-th failed attempt is reported.
     */
    private static final int REPORT_EVERY = 10;
    /**
     * A connection that stayed open this long counts as working, so the next failure starts with short delays
     * again. A master that rejects the wrapper right away keeps the delays growing.
     */
    private static final long STABLE_CONNECTION_MILLIS = 60_000;

    /**
     * Channel of the current (or last) connection attempt. It is closed while there is no connection.
     */
    @Getter
    private volatile Channel channel;
    private final String address;
    private final int port;
    private final EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private final Bootstrap bootstrap;
    private int failedAttempts;
    private volatile boolean closed;

    /**
     * @param sslContext encrypts the connection and checks the master's certificate
     */
    public WrapperClient(String address, int port, SslContext sslContext) {
        this.address = address;
        this.port = port;
        bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        // Length-prefixed JSON frames instead of Java serialization
                        socketChannel.pipeline().addLast("tls", sslContext.newHandler(socketChannel.alloc(), address, port));
                        PacketPipeline.configure(socketChannel.pipeline());
                        socketChannel.pipeline().addLast(new WrapperHandler());
                    }
                });
    }

    @Override
    public void run() {
        connect();
    }

    private void connect() {
        if (closed) {
            return;
        }
        var connectFuture = bootstrap.connect(address, port);
        channel = connectFuture.channel();
        connectFuture.addListener((ChannelFuture future) -> {
            if (!future.isSuccess()) {
                retry("Could not connect to the master %s", address + ":" + port);
                return;
            }
            var connectedAt = System.currentTimeMillis();
            future.channel().closeFuture().addListener(closeFuture -> {
                if (System.currentTimeMillis() - connectedAt >= STABLE_CONNECTION_MILLIS) {
                    failedAttempts = 0;
                }
                retry("The connection to the master %s was lost", address + ":" + port);
            });
        });
    }

    private void retry(String message, String master) {
        if (closed) {
            return;
        }
        failedAttempts++;
        var delay = Math.min(MAX_RETRY_SECONDS, MIN_RETRY_SECONDS << Math.min(failedAttempts - 1, 4));
        if (delay < MAX_RETRY_SECONDS || failedAttempts % REPORT_EVERY == 0) {
            Console.error(message + " (attempt %s, next attempt in %s seconds)", master, failedAttempts, delay);
        }
        eventLoopGroup.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    /**
     * Closes the connection for good, without reconnecting.
     */
    public void close() {
        closed = true;
        var current = channel;
        if (current != null) {
            current.close().syncUninterruptibly();
        }
        eventLoopGroup.shutdownGracefully();
    }

}
