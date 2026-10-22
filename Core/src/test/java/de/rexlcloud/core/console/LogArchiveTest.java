package de.rexlcloud.core.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class LogArchiveTest {

    @TempDir
    Path logs;

    @Test
    void archivesHaveSortableNamesWithoutColons() throws IOException {
        var log = Files.writeString(logs.resolve("latest.log"), "line 1\n");

        var archive = Console.archiveLog(logs, log, LocalDateTime.of(2026, 10, 19, 14, 3, 7));

        assertEquals("2026-10-19_14-03-07.zip", archive.getFileName().toString());
        assertFalse(Files.exists(log));
        try (var zip = new ZipFile(archive.toFile())) {
            var entry = zip.getEntry("2026-10-19_14-03-07.log");
            assertNotNull(entry);
            assertEquals("line 1\n", new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void archivesOfTheSameSecondDoNotOverwriteEachOther() throws IOException {
        var time = LocalDateTime.of(2026, 10, 19, 2, 0, 0);
        var first = Console.archiveLog(logs, Files.writeString(logs.resolve("latest.log"), "a"), time);
        var second = Console.archiveLog(logs, Files.writeString(logs.resolve("latest.log"), "b"), time);

        assertEquals("2026-10-19_02-00-00.zip", first.getFileName().toString());
        assertEquals("2026-10-19_02-00-00-1.zip", second.getFileName().toString());
        try (var zip = new ZipFile(second.toFile())) {
            assertNotNull(zip.getEntry("2026-10-19_02-00-00-1.log"));
        }
    }

}
