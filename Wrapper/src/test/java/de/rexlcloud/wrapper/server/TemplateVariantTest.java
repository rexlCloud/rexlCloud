package de.rexlcloud.wrapper.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemplateVariantTest {

    @TempDir
    Path group;

    @Test
    void everyVariantIsUsedButNeverDefault() throws IOException {
        for (var name : new String[]{"default", "map-desert", "map-forest", "map-ice"}) {
            Files.createDirectories(group.resolve(name));
        }
        Files.writeString(group.resolve("notes.txt"), "files are no variants");

        var picked = new HashSet<String>();
        for (int i = 0; i < 300; i++) {
            Server.randomVariant(group.toFile()).map(File::getName).ifPresent(picked::add);
        }

        assertEquals(Set.of("map-desert", "map-forest", "map-ice"), picked);
    }

    @Test
    void groupsWithOnlyDefaultHaveNoVariant() throws IOException {
        Files.createDirectories(group.resolve("default"));
        assertTrue(Server.randomVariant(group.toFile()).isEmpty());
        assertTrue(Server.randomVariant(group.resolve("missing").toFile()).isEmpty());
    }

}
