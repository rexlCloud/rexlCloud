package de.rexlcloud.master.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TemplateUploadsTest {

    @TempDir
    Path folder;

    private Path zip(String... entries) throws IOException {
        var zip = folder.resolve("upload.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (var entry : entries) {
                out.putNextEntry(new ZipEntry(entry));
                out.write(("content of " + entry).getBytes());
                out.closeEntry();
            }
        }
        return zip;
    }

    @Test
    void filesAreReplacedAndOthersKept() throws IOException {
        var template = folder.resolve("templates/Lobby/default");
        Files.createDirectories(template.resolve("world"));
        Files.writeString(template.resolve("server.properties"), "old");
        Files.writeString(template.resolve("server.jar"), "the installed jar");

        var count = TemplateUploads.extract(zip("server.properties", "world/level.dat"), template);

        assertEquals(2, count);
        assertEquals("content of server.properties", Files.readString(template.resolve("server.properties")));
        assertEquals("content of world/level.dat", Files.readString(template.resolve("world/level.dat")));
        assertEquals("the installed jar", Files.readString(template.resolve("server.jar")), "not in the upload: kept");
        try (var files = Files.list(template)) {
            assertTrue(files.noneMatch(file -> file.getFileName().toString().startsWith(".rexlcloud-")),
                    "no temporary file left");
        }
    }

    @Test
    void pathsOutsideTheTemplateAreRefused() throws IOException {
        var template = folder.resolve("templates/Lobby/default");
        Files.createDirectories(template);

        var e = assertThrows(IOException.class, () -> TemplateUploads.extract(zip("../../evil.txt"), template));
        assertTrue(e.getMessage().contains("../../evil.txt"));
        assertFalse(Files.exists(folder.resolve("templates/evil.txt")));
    }

    @Test
    void variantNamesAreChecked() {
        assertTrue(TemplateUploads.VARIANT.matcher("map-desert").matches());
        assertFalse(TemplateUploads.VARIANT.matcher("../default").matches());
        assertFalse(TemplateUploads.VARIANT.matcher("").matches());
    }

}
