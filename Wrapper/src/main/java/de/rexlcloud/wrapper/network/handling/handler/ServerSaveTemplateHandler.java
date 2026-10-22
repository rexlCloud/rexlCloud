package de.rexlcloud.wrapper.network.handling.handler;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerSaveTemplatePacket;
import de.rexlcloud.core.network.packets.TemplateUploadPacket;
import de.rexlcloud.core.network.packets.handling.IPacketHandler;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import de.rexlcloud.wrapper.server.Server;
import de.rexlcloud.wrapper.server.ServerSnapshot;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Sends the files of a running server to the master, which saves them as a template variant.
 */
public class ServerSaveTemplateHandler implements IPacketHandler<ServerSaveTemplatePacket> {

    private static final int CHUNK = 1024 * 1024;

    @Override
    public PacketType getType() {
        return PacketType.SERVER_SAVE_TEMPLATE;
    }

    @Override
    public void handlePacket(ChannelHandlerContext ctx, ServerSaveTemplatePacket packet) {
        var server = RexlCloudWrapper.getInstance().getServerManager()
                .getServerByNameAndKey(packet.getName(), packet.getKey()).orElse(null);
        var channel = ctx.channel();
        if (server == null || !server.isOnline() || server.getServerFolder() == null) {
            fail(channel, packet, "the server is not online on this wrapper");
            return;
        }
        // Packing can take a while; the connection's other packets go on meanwhile
        Thread.ofVirtual().start(() -> upload(channel, server, packet));
    }

    private void upload(Channel channel, Server server, ServerSaveTemplatePacket packet) {
        try {
            if (server.getGroup().getType() == ServerType.SERVER && !server.getGroup().isCustomJar()) {
                // Writes the worlds to disk. Not "save-all flush": on 1.8 it can keep the main thread busy until the
                // watchdog stops the server
                server.execute("save-all");
                Thread.sleep(5000);
            }
            var zip = Files.createTempFile("rexlcloud-save-", ".zip");
            try {
                var digest = MessageDigest.getInstance("SHA-256");
                int files;
                try (var out = new DigestOutputStream(Files.newOutputStream(zip), digest)) {
                    files = ServerSnapshot.zip(server.getServerFolder().toPath(), out);
                }
                var sha256 = HexFormat.of().formatHex(digest.digest());
                var size = Files.size(zip);
                try (InputStream in = Files.newInputStream(zip)) {
                    var buffer = new byte[CHUNK];
                    var chunk = 0;
                    long sent = 0;
                    do {
                        var read = in.readNBytes(buffer, 0, CHUNK);
                        sent += read;
                        var last = sent >= size;
                        // Waits for each chunk, so a large world does not pile up in memory
                        channel.writeAndFlush(new PacketContainer(PacketType.TEMPLATE_UPLOAD, new TemplateUploadPacket(
                                packet.getRequestId(), chunk++, last, Arrays.copyOf(buffer, read),
                                last ? sha256 : null, null))).sync();
                    } while (sent < size);
                }
                Console.info("Sent %s file(s) of %s to the master for the template", files, server.getName());
            } finally {
                Files.deleteIfExists(zip);
            }
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            fail(channel, packet, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(channel, packet, "interrupted");
        }
    }

    private static void fail(Channel channel, ServerSaveTemplatePacket packet, String error) {
        Console.error("%s could not be saved as a template: %s", packet.getName(), error);
        channel.writeAndFlush(new PacketContainer(PacketType.TEMPLATE_UPLOAD,
                new TemplateUploadPacket(packet.getRequestId(), 0, true, null, null, error)));
    }

}
