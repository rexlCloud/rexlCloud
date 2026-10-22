package de.rexlcloud.master.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateFilesTest {

    @TempDir
    Path templates;

    private TemplateFiles files() {
        return new TemplateFiles(templates);
    }

    private static ByteArrayInputStream content(String text) {
        return new ByteArrayInputStream(text.getBytes());
    }

    @Test
    void writesListsReadsAndDeletesFiles() throws Exception {
        var files = files();
        files.write("Lobby", "default", "plugins/MyPlugin/config.yml", content("a: 1"), 1024);
        files.createFolder("Lobby", "default", "world");

        var list = files.list("Lobby", "default", "");
        assertEquals("plugins", list.get(0).getAsJsonObject().get("name").getAsString(), "folders first");
        assertTrue(list.get(0).getAsJsonObject().get("directory").getAsBoolean());
        assertEquals("a: 1", Files.readString(files.file("Lobby", "default", "plugins/MyPlugin/config.yml")));

        files.delete("Lobby", "default", "plugins");
        assertFalse(Files.exists(templates.resolve("Lobby/default/plugins")));
    }

    @Test
    void nothingOutsideTheTemplateCanBeReached() {
        var files = files();
        assertThrows(TemplateFiles.TemplateFileException.class, () -> files.resolve("Lobby", "default", "../../etc/passwd"));
        assertThrows(TemplateFiles.TemplateFileException.class, () -> files.resolve("Lobby", "../x", "a"));
        assertThrows(TemplateFiles.TemplateFileException.class, () -> files.resolve("../Lobby", "default", "a"));
        assertThrows(TemplateFiles.TemplateFileException.class, () -> files.resolve("global", "other", "a"));
        assertThrows(TemplateFiles.TemplateFileException.class,
                () -> files.resolve("Lobby", "default", "plugins/.rexlcloud-upload-x"), "the cloud's temporary files");
    }

    @Test
    void largeFilesAreRefusedAndLeaveNothingBehind() throws Exception {
        var files = files();
        var e = assertThrows(TemplateFiles.TemplateFileException.class,
                () -> files.write("Lobby", "default", "server.jar", content("x".repeat(2048)), 1024));
        assertTrue(e.getMessage().contains("larger"));
        try (var left = Files.list(templates.resolve("Lobby/default"))) {
            assertEquals(0, left.count());
        }
    }

    @Test
    void variantsCanBeDeletedButNotTheDefaultTemplate() throws Exception {
        var files = files();
        files.write("Lobby", "map-desert", "world/level.dat", content("x"), 1024);
        files.write("Lobby", "default", "server.properties", content("x"), 1024);

        assertEquals(List.of("default", "map-desert"), files.variants("Lobby"));
        files.delete("Lobby", "map-desert", "");
        assertEquals(List.of("default"), files.variants("Lobby"));

        assertThrows(TemplateFiles.TemplateFileException.class, () -> files.delete("Lobby", "default", ""));
        assertThrows(TemplateFiles.TemplateFileException.class, () -> files.delete("global", "server", "/"));
    }

}
