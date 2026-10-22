package de.rexlcloud.core.server.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemplatesTest {

    @TempDir
    Path root;

    @Test
    void templateNames() {
        assertTrue(Templates.isValidName("Lobby"));
        assertTrue(Templates.isValidName("Sky_Wars-2"));
        assertTrue(Templates.isValidName(Templates.GLOBAL_SERVER));
        assertTrue(Templates.isValidName(Templates.GLOBAL_PROXY));
        assertFalse(Templates.isValidName("global"), "reserved for the global templates");
        assertFalse(Templates.isValidName("Global"));
        assertFalse(Templates.isValidName("global/other"));
        assertFalse(Templates.isValidName(".."));
        assertFalse(Templates.isValidName("a/b"));
        assertFalse(Templates.isValidName(null));
    }

    @Test
    void filesCannotLeaveTheirTemplate() {
        assertEquals(root.resolve("Lobby/default/server.jar").toAbsolutePath().normalize(),
                Templates.file(root, "Lobby", "default/server.jar"));
        for (var path : new String[]{"../Proxy/default/proxy.jar", "default/../../x", "/etc/passwd", "default\\x",
                "C:/Windows/x", "", "."}) {
            assertThrows(IllegalArgumentException.class, () -> Templates.file(root, "Lobby", path), path);
        }
        assertThrows(IllegalArgumentException.class, () -> Templates.file(root, "../x", "a"));
    }

    @Test
    void manifestListsFilesWithSlashesAndSkipsTemporaryFiles() throws IOException {
        var lobby = root.resolve("Lobby");
        Files.createDirectories(lobby.resolve("default/plugins"));
        Files.writeString(lobby.resolve("default/server.properties"), "motd=hi");
        Files.writeString(lobby.resolve("default/plugins/a.yml"), "");
        Files.writeString(lobby.resolve("default/.rexlcloud-download-1.tmp"), "half");

        var manifest = Templates.manifest(lobby);

        assertEquals(Set.of("default/server.properties", "default/plugins/a.yml"), manifest.keySet());
        var properties = manifest.get("default/server.properties");
        assertEquals(7, properties.size());
        assertEquals(Templates.sha256(new ByteArrayInputStream("motd=hi".getBytes(StandardCharsets.UTF_8))),
                properties.sha256());
        assertTrue(Templates.manifest(root.resolve("Missing")).isEmpty());
    }

    @Test
    void changedFilesGetANewHash() throws IOException {
        var file = Files.writeString(root.resolve("a.txt"), "one");
        var before = Templates.manifest(root).get("a.txt").sha256();

        Files.writeString(file, "two");
        // Same size, so only the modification time tells the cache it changed
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5_000));

        assertNotEquals(before, Templates.manifest(root).get("a.txt").sha256());
    }

}
