package de.rexlcloud.core.server.files;

import com.sun.net.httpserver.HttpServer;
import de.rexlcloud.core.server.ServerType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerJarInstallerTest {

    @TempDir
    Path folder;
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/paper.jar", exchange -> {
            var body = "paper jar".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/missing.jar", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ServerJar download(String path) {
        return new ServerJar("Paper", "26.3", null, ServerType.SERVER, baseUrl + path);
    }

    @Test
    void downloadsTheJar() throws IOException {
        var target = folder.resolve("templates/Lobby/default/server.jar");

        new ServerJarInstaller(folder.resolve("cache")).install(download("/paper.jar"), target);

        assertEquals("paper jar", Files.readString(target));
    }

    @Test
    void aFailedDownloadKeepsTheOldJar() throws IOException {
        var target = folder.resolve("server.jar");
        Files.writeString(target, "old jar");

        var installer = new ServerJarInstaller(folder.resolve("cache"));
        var failure = assertThrows(IOException.class, () -> installer.install(download("/missing.jar"), target));

        assertTrue(failure.getMessage().contains("404"), failure.getMessage());
        assertEquals("old jar", Files.readString(target));
        try (var files = Files.list(folder)) {
            assertEquals(1, files.count(), "no temporary files left behind");
        }
    }

    @Test
    void spigotBuiltBeforeIsTakenFromTheCache() throws IOException {
        var cache = folder.resolve("cache");
        Files.createDirectories(cache.resolve("spigot"));
        Files.writeString(cache.resolve("spigot/spigot-26.3.jar"), "built spigot");
        var spigot = new ServerJar("Spigot", "26.3", null, ServerType.SERVER,
                "https://hub.spigotmc.org/versions/26.3.json", ServerJar.Source.SPIGOT_BUILD_TOOLS);
        var target = folder.resolve("server.jar");

        new ServerJarInstaller(cache).install(spigot, target);

        assertEquals("built spigot", Files.readString(target));
    }

    @Test
    void invalidSpigotVersionsAreNotPassedToBuildTools() {
        var installer = new ServerJarInstaller(folder.resolve("cache"));
        assertThrows(IOException.class, () -> installer.buildSpigot("26.3 --dev"));
    }

}
