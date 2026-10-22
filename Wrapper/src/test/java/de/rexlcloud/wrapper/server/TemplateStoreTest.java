package de.rexlcloud.wrapper.server;

import de.rexlcloud.core.network.AesUtil;
import de.rexlcloud.core.network.packets.TemplateDeletePacket;
import de.rexlcloud.core.network.packets.TemplateFilePacket;
import de.rexlcloud.core.server.template.Templates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TemplateStoreTest {

    private static final UUID KEY = UUID.randomUUID();

    @TempDir
    Path servers;

    private Path root() {
        return servers.resolve("templates");
    }

    private TemplateStore store() {
        return new TemplateStore(root(), KEY);
    }

    private static String sha256(byte[] data) throws IOException {
        return Templates.sha256(new ByteArrayInputStream(data));
    }

    /**
     * Sends data the way the master does, in chunks of the given size.
     */
    private static void send(TemplateStore store, String template, String path, byte[] data, int chunkSize,
                             String sha256) {
        var chunks = Math.max(1, (data.length + chunkSize - 1) / chunkSize);
        for (int chunk = 0; chunk < chunks; chunk++) {
            var part = Arrays.copyOfRange(data, chunk * chunkSize, Math.min(data.length, (chunk + 1) * chunkSize));
            store.receive(new TemplateFilePacket(template, path, data.length, sha256, chunk, chunks,
                    AesUtil.encrypt(part, KEY)));
        }
    }

    @Test
    void filesArriveInChunks() throws IOException {
        var store = store();
        var data = new byte[2500];
        new java.util.Random(1).nextBytes(data);

        send(store, "Lobby", "default/server.jar", data, 1000, sha256(data));

        assertArrayEquals(data, Files.readAllBytes(root().resolve("Lobby/default/server.jar")));
        assertEquals(sha256(data), store.state().get("Lobby").get("default/server.jar").sha256());
        try (var leftovers = Files.list(servers.resolve(".template-transfers"))) {
            assertEquals(0, leftovers.count(), "no temporary files left");
        }
    }

    @Test
    void emptyFilesWork() throws IOException {
        send(store(), Templates.GLOBAL_SERVER, "plugins/empty.yml", new byte[0], 1000, sha256(new byte[0]));
        assertEquals(0, Files.size(root().resolve("global/server/plugins/empty.yml")));
    }

    @Test
    void aWrongChecksumKeepsTheOldFile() throws IOException {
        var file = root().resolve("Lobby/default/server.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "old");

        send(store(), "Lobby", "default/server.properties", "new".getBytes(), 1000, sha256("other".getBytes()));

        assertEquals("old", Files.readString(file));
    }

    @Test
    void missingChunksAreRejected() throws IOException {
        var store = store();
        var data = "0123456789".getBytes();
        store.receive(new TemplateFilePacket("Lobby", "a.txt", 10, sha256(data), 0, 2,
                AesUtil.encrypt(Arrays.copyOfRange(data, 0, 5), KEY)));
        // Chunk 1 never arrives; the file is sent again from the start
        send(store, "Lobby", "a.txt", data, 3, sha256(data));

        assertEquals("0123456789", Files.readString(root().resolve("Lobby/a.txt")));
    }

    @Test
    void pathsOutsideTheTemplatesAreRefused() throws IOException {
        var data = "x".getBytes();
        send(store(), "Lobby", "../../evil.txt", data, 1000, sha256(data));
        send(store(), "..", "evil.txt", data, 1000, sha256(data));

        assertFalse(Files.exists(servers.resolve("evil.txt")));
        assertFalse(Files.exists(servers.getParent().resolve("evil.txt")));
    }

    @Test
    void deletesFilesEmptyFoldersAndOldTemplates() throws IOException {
        var store = store();
        var data = "x".getBytes();
        send(store, "Lobby", "default/plugins/a/b.yml", data, 1000, sha256(data));
        send(store, "Lobby", "default/server.properties", data, 1000, sha256(data));
        send(store, "Old", "default/server.jar", data, 1000, sha256(data));
        send(store, "Static", "default/server.jar", data, 1000, sha256(data));

        store.delete(new TemplateDeletePacket("Lobby", List.of("default/plugins/a/b.yml"), null));
        assertFalse(Files.exists(root().resolve("Lobby/default/plugins")), "empty folders are removed");
        assertTrue(Files.exists(root().resolve("Lobby/default/server.properties")));

        store.delete(new TemplateDeletePacket(null, List.of(),
                Set.of("Lobby", "Static", Templates.GLOBAL_SERVER, Templates.GLOBAL_PROXY)));
        assertFalse(Files.exists(root().resolve("Old")));
        assertTrue(Files.exists(root().resolve("Static/default/server.jar")), "static groups keep their jar");
        assertTrue(store.state().containsKey(Templates.GLOBAL_SERVER));
    }

}
