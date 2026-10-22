package de.rexlcloud.master.network;

import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.codec.PacketPipeline;
import de.rexlcloud.core.network.tls.PinnedTrustManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContextBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MasterTlsTest {

    @TempDir
    Path folder;
    private EventLoopGroup group;
    private Channel server;
    private MasterTls tls;
    private final CompletableFuture<Object> received = new CompletableFuture<>();

    @BeforeEach
    void startMaster() throws Exception {
        tls = MasterTls.load(folder.resolve("tls"));
        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast("tls", tls.getSslContext().newHandler(channel.alloc()));
                        PacketPipeline.configure(channel.pipeline());
                        channel.pipeline().addLast(new SimpleChannelInboundHandler<PacketContainer>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, PacketContainer packet) {
                                received.complete(packet.getPacket());
                            }
                        });
                    }
                }).bind("127.0.0.1", 0).sync().channel();
    }

    @AfterEach
    void stop() {
        server.close();
        group.shutdownGracefully();
    }

    /**
     * Connects like a wrapper and sends a packet.
     */
    private void send(PinnedTrustManager trustManager, String text) throws Exception {
        var context = SslContextBuilder.forClient().trustManager(trustManager).protocols("TLSv1.3")
                .endpointIdentificationAlgorithm(null).build();
        var port = ((InetSocketAddress) server.localAddress()).getPort();
        var client = new Bootstrap().group(group).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast("tls", context.newHandler(channel.alloc(), "127.0.0.1", port));
                        PacketPipeline.configure(channel.pipeline());
                    }
                }).connect("127.0.0.1", port).sync().channel();
        client.writeAndFlush(new PacketContainer(PacketType.SERVER_STOP, text));
    }

    @Test
    void keysAreCreatedOnceAndKeepTheirFingerprint() throws Exception {
        assertTrue(Files.isRegularFile(folder.resolve("tls/master.crt")));
        assertTrue(Files.readString(folder.resolve("tls/master.key")).startsWith("-----BEGIN PRIVATE KEY-----"));
        assertTrue(tls.getFingerprint().matches("([0-9A-F]{2}:){31}[0-9A-F]{2}"), tls.getFingerprint());
        assertEquals(tls.getFingerprint(), MasterTls.load(folder.resolve("tls")).getFingerprint());
    }

    @Test
    void aWrapperWithThePinnedFingerprintConnects() throws Exception {
        send(new PinnedTrustManager(() -> tls.getFingerprint().toLowerCase().replace(":", ""), fingerprint -> fail()), "hello");
        assertEquals("hello", received.get(10, TimeUnit.SECONDS));
    }

    @Test
    void anotherMasterIsRefused() throws Exception {
        var otherFingerprint = MasterTls.load(folder.resolve("other")).getFingerprint();
        send(new PinnedTrustManager(() -> otherFingerprint, fingerprint -> fail()), "secret");
        assertThrows(Exception.class, () -> received.get(3, TimeUnit.SECONDS), "nothing may arrive");
    }

    @Test
    void withoutAPinTheFirstMasterIsTrustedAndRemembered() throws Exception {
        var remembered = new AtomicReference<String>();
        send(new PinnedTrustManager(() -> remembered.get() == null ? "" : remembered.get(), remembered::set), "first");
        assertEquals("first", received.get(10, TimeUnit.SECONDS));
        assertEquals(tls.getFingerprint(), remembered.get());
    }

}
