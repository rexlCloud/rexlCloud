package de.rexlcloud.launcher.dependencies;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UpdaterTest {

    @TempDir
    Path folder;
    private HttpServer server;
    /**
     * Like https://github.com/rexlCloud/rexlCloud/releases
     */
    private String repository;
    /**
     * Served files by path below the releases page, e.g. download/v1.0.5/master.jar.
     */
    private final Map<String, byte[]> files = new HashMap<>();
    /**
     * The tag releases/latest redirects to, {@code null} while there is no release.
     */
    private String latest;

    @BeforeEach
    void startRepository() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath().substring(1);
            // Like GitHub: releases/latest answers with a redirect to the newest release's tag
            if (path.equals("releases/latest") && latest != null) {
                exchange.getResponseHeaders().add("Location", repository + "/tag/v" + latest);
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }
            var body = path.startsWith("releases/") ? files.get(path.substring("releases/".length())) : null;
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        repository = "http://localhost:" + server.getAddress().getPort() + "/releases";
    }

    @AfterEach
    void stopRepository() {
        server.stop(0);
    }

    /**
     * Makes {@code version} the newest release with {@code <type>.jar} and its checksum.
     */
    private void publish(String type, String version, String jarContent) throws IOException {
        latest = version;
        var jar = jarContent.getBytes(StandardCharsets.UTF_8);
        var path = "download/v" + version + "/" + type + ".jar";
        files.put(path, jar);
        var temp = Files.write(folder.resolve("sha-source"), jar);
        // As sha256sum writes it
        files.put(path + ".sha256", (Updater.getSha256(temp) + "  " + type + ".jar\n").getBytes(StandardCharsets.UTF_8));
        Files.delete(temp);
    }

    private Path config() {
        return folder.resolve("launcher.cnf");
    }

    private String jar(String type) throws IOException {
        return Files.readString(folder.resolve("libs/" + type + ".jar"));
    }

    @Test
    void firstStartInstallsTheNewestVersion() throws IOException {
        publish("master", "1.0.5", "master 1.0.5");

        var updater = new Updater(folder, repository);
        updater.download("master");

        assertEquals("master 1.0.5", jar("master"));
        assertEquals(List.of("auto_update=true", "master_version=1.0.5"), Files.readAllLines(config()));
    }

    @Test
    void anUpdateReplacesTheJarAndKeepsTheConfiguration() throws IOException {
        // Older launchers crashed here: they replaced launcher.cnf with a folder
        publish("master", "1.0.4", "master 1.0.4");
        new Updater(folder, repository).download("master");
        Files.writeString(config(), "auto_update=true\nmaster_version=1.0.4\nwrapper_version=1.0.4\n");
        publish("master", "1.0.5", "master 1.0.5");

        var updater = new Updater(folder, repository);
        updater.download("master");

        assertEquals("master 1.0.5", jar("master"));
        assertTrue(Files.isRegularFile(config()));
        assertEquals(List.of("auto_update=true", "master_version=1.0.5", "wrapper_version=1.0.4"),
                Files.readAllLines(config()));
        assertEquals("1.0.5", updater.getVersion("master"));
    }

    @Test
    void withoutAutoUpdateTheInstalledVersionStays() throws IOException {
        publish("wrapper", "1.0.4", "wrapper 1.0.4");
        new Updater(folder, repository).download("wrapper");
        Files.writeString(config(), "auto_update=false\nwrapper_version=1.0.4\n");
        publish("wrapper", "1.0.5", "wrapper 1.0.5");

        var updater = new Updater(folder, repository);
        updater.download("wrapper");

        assertEquals("wrapper 1.0.4", jar("wrapper"));
        assertEquals("1.0.4", updater.getVersion("wrapper"));
    }

    @Test
    void aDamagedDownloadKeepsTheOldJar() throws IOException {
        publish("master", "1.0.4", "master 1.0.4");
        new Updater(folder, repository).download("master");
        publish("master", "1.0.5", "master 1.0.5");
        files.put("download/v1.0.5/master.jar", "truncated".getBytes(StandardCharsets.UTF_8));

        var failure = assertThrows(IOException.class, () -> new Updater(folder, repository).download("master"));

        assertTrue(failure.getMessage().contains("damaged"), failure.getMessage());
        assertEquals("master 1.0.4", jar("master"));
        try (var libs = Files.list(folder.resolve("libs"))) {
            assertEquals(1, libs.count(), "no temporary download left");
        }
    }

    @Test
    void startsTheInstalledJarWhenGitHubIsDown() throws IOException {
        publish("master", "1.0.5", "master 1.0.5");
        new Updater(folder, repository).download("master");
        server.stop(0);

        var updater = new Updater(folder, repository);
        updater.download("master");

        assertEquals("1.0.5", updater.getVersion("master"));
        assertEquals("master 1.0.5", jar("master"));
    }

    @Test
    void oldAndBrokenConfigurationLinesAreIgnored() throws IOException {
        publish("master", "1.0.5", "master 1.0.5");
        Files.writeString(config(), "auto_update=true\nversion=1.0.3\nbroken line\n=x\n");

        new Updater(folder, repository).download("master");

        assertEquals(List.of("auto_update=true", "master_version=1.0.5"), Files.readAllLines(config()));
    }

    @Test
    void withoutAnyReleaseTheFirstStartFails() {
        var failure = assertThrows(IOException.class, () -> new Updater(folder, repository).download("master"));

        assertTrue(failure.getMessage().contains("No release"), failure.getMessage());
    }

    @Test
    void theLauncherReplacesItselfWithANewerRelease() throws IOException {
        publish("launcher", "1.0.20", "launcher 1.0.20");
        var own = Files.writeString(folder.resolve("launcher.jar"), "launcher 1.0.19");

        assertEquals("1.0.20", new Updater(folder, repository).updateLauncher(own, "1.0.19"));

        assertEquals("launcher 1.0.20", Files.readString(own));
        try (var files = Files.list(folder)) {
            assertTrue(files.noneMatch(file -> file.getFileName().toString().endsWith(".download")));
        }
    }

    @Test
    void theLauncherStaysWhenItIsCurrentOrUpdatesAreOff() throws IOException {
        publish("launcher", "1.0.20", "launcher 1.0.20");
        var own = Files.writeString(folder.resolve("launcher.jar"), "launcher 1.0.20");
        assertNull(new Updater(folder, repository).updateLauncher(own, "1.0.20"));

        Files.writeString(own, "launcher 1.0.19");
        Files.writeString(config(), "auto_update=false\n");
        assertNull(new Updater(folder, repository).updateLauncher(own, "1.0.19"));
        assertNull(new Updater(folder, repository).updateLauncher(own, null), "development builds");
        assertEquals("launcher 1.0.19", Files.readString(own));
    }

    @Test
    void aDamagedLauncherDownloadIsNotInstalled() throws IOException {
        publish("launcher", "1.0.20", "launcher 1.0.20");
        files.put("download/v1.0.20/launcher.jar.sha256", "0".repeat(64).getBytes(StandardCharsets.UTF_8));
        var own = Files.writeString(folder.resolve("launcher.jar"), "launcher 1.0.19");

        assertNull(new Updater(folder, repository).updateLauncher(own, "1.0.19"));
        assertEquals("launcher 1.0.19", Files.readString(own));
    }

}
