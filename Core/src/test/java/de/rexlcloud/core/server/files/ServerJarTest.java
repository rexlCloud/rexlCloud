package de.rexlcloud.core.server.files;

import de.rexlcloud.core.server.ServerType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

public class ServerJarTest {

    @Test
    void testNameMatcher() {
        Assertions.assertDoesNotThrow(() ->
                new ServerJar("test", "1.0", ServerType.SERVER, "https://regex101.com")
        );
    }

    @Test
    void spigotJarsAreBuiltAndNamedLikeOtherJars() {
        var spigot = new ServerJar("Spigot", "26.3", null, ServerType.SERVER,
                "https://hub.spigotmc.org/versions/26.3.json", ServerJar.Source.SPIGOT_BUILD_TOOLS);
        Assertions.assertEquals("SPIGOT_26_3", spigot.getFullText());
        Assertions.assertEquals(ServerJar.Source.SPIGOT_BUILD_TOOLS, spigot.getSource());
        Assertions.assertEquals(ServerJar.Source.DOWNLOAD,
                new ServerJar("Paper", "26.3", ServerType.SERVER, "https://fill.papermc.io/x.jar").getSource());
        Assertions.assertThrows(RuntimeException.class, () -> new ServerJar("Spigot", null, "1", ServerType.SERVER,
                "https://hub.spigotmc.org/versions/x.json", ServerJar.Source.SPIGOT_BUILD_TOOLS));
    }

    @Test
    void spigotVersionsNeedAMatchingJavaVersion() {
        // Java 25 is class file version 69
        Assertions.assertTrue(ServerJarManager.buildableWith("{\"javaVersions\": [69, 70]}", 69));
        Assertions.assertFalse(ServerJarManager.buildableWith("{\"javaVersions\": [61, 65]}", 69));
        // Versions without javaVersions need Java 8
        Assertions.assertFalse(ServerJarManager.buildableWith("{\"name\": \"582b\"}", 69));
        Assertions.assertTrue(ServerJarManager.buildableWith("{\"name\": \"582b\"}", 52));
    }

    @Test
    void versionsSortNumerically() {
        var versions = new java.util.ArrayList<>(List.of("1.21.9", "26.1", "1.21.10", "1.8.8"));
        versions.sort(Comparator.comparing(ServerJarManager::versionKey).reversed());
        Assertions.assertEquals(List.of("26.1", "1.21.10", "1.21.9", "1.8.8"), versions);
    }

}
