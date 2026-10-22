package de.rexlcloud.wrapper.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ServerSnapshotTest {

    @TempDir
    Path server;

    private void file(String path) throws Exception {
        var file = server.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, path);
    }

    @Test
    void theTemplateGetsTheServerButNotWhatEveryStartRecreates() throws Exception {
        for (var path : new String[]{"server.properties", "world/level.dat", "world/session.lock",
                "world/region/r.0.0.mca", "plugins/MyPlugin.jar", "plugins/MyPlugin/config.yml",
                "plugins/bridge-1.0.17.jar", "logs/latest.log", "cache/mojang_1.21.jar", "libraries/a.jar",
                "versions/26.3/paper.jar", "cloud.json", "eula.txt", "server.jar"}) {
            file(path);
        }

        var bytes = new ByteArrayOutputStream();
        var count = ServerSnapshot.zip(server, bytes);

        var entries = new TreeSet<String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
            }
        }
        assertEquals(new TreeSet<>(java.util.List.of("server.properties", "world/level.dat", "world/region/r.0.0.mca",
                "plugins/MyPlugin.jar", "plugins/MyPlugin/config.yml")), entries);
        assertEquals(5, count);
    }

    @Test
    void foldersAreOnlySkippedAtTheTop() {
        assertTrue(ServerSnapshot.isSaved("plugins/MyPlugin/logs/data.yml"), "a plugin's own logs folder stays");
        assertTrue(ServerSnapshot.isSaved("plugins/bridge-helper/config.yml"));
        assertFalse(ServerSnapshot.isSaved("plugins/proxy-1.0.17.jar"));
    }

}
