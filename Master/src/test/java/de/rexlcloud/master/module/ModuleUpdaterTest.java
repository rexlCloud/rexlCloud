package de.rexlcloud.master.module;

import com.sun.net.httpserver.HttpServer;
import de.rexlcloud.core.release.CloudReleases;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModuleUpdaterTest {

    @TempDir
    Path modules;
    private HttpServer server;
    private CloudReleases releases;
    private final Map<String, byte[]> files = new HashMap<>();

    @BeforeEach
    void startRepository() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var body = files.get(exchange.getRequestURI().getPath().substring("/releases/".length()));
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        releases = new CloudReleases("http://127.0.0.1:" + server.getAddress().getPort() + "/releases");
    }

    @AfterEach
    void stopRepository() {
        server.stop(0);
    }

    private static byte[] moduleJar(String name, String version) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("module.json"));
            zip.write(("{\"name\":\"" + name + "\",\"main\":\"x\",\"version\":\"" + version + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    /**
     * Attaches {@code <artifact>-<version>.jar} and its checksum to the release {@code v<version>}, like the release workflow.
     */
    private void publish(String artifact, String name, String version) throws IOException {
        var jar = moduleJar(name, version);
        var path = "download/v" + version + "/" + artifact + "-" + version + ".jar";
        files.put(path, jar);
        var temp = Files.write(modules.resolveSibling("sha-" + version), jar);
        files.put(path + ".sha256", (CloudReleases.sha256(temp) + "  " + artifact + ".jar\n").getBytes(StandardCharsets.UTF_8));
        Files.delete(temp);
    }

    private String installedVersion(String fileName) {
        return ModuleUpdater.readModuleInfo(modules.resolve(fileName)).version();
    }

    @Test
    void anInstalledRestModuleIsBroughtToTheMastersVersion() throws IOException {
        Files.write(modules.resolve("RestModule-1.0.12.jar"), moduleJar("RestModule", "1.0.12"));
        publish("restmodule", "RestModule", "1.0.14");

        new ModuleUpdater(modules, releases).update("1.0.14");

        assertFalse(Files.exists(modules.resolve("RestModule-1.0.12.jar")), "the old jar is gone");
        assertEquals("1.0.14", installedVersion("RestModule-1.0.14.jar"));
        try (var files = Files.list(modules)) {
            assertEquals(1, files.count(), "no temporary download left");
        }
    }

    @Test
    void aModuleThatIsNotInstalledIsNotAdded() throws IOException {
        publish("restmodule", "RestModule", "1.0.14");

        new ModuleUpdater(modules, releases).update("1.0.14");

        try (var files = Files.list(modules)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void thirdPartyAndCurrentModulesAreLeftAlone() throws IOException {
        Files.write(modules.resolve("MyModule.jar"), moduleJar("MyModule", "1.0"));
        Files.write(modules.resolve("RestModule.jar"), moduleJar("RestModule", "1.0.14"));

        new ModuleUpdater(modules, releases).update("1.0.14");

        assertEquals("1.0", installedVersion("MyModule.jar"));
        assertEquals("1.0.14", installedVersion("RestModule.jar"));
    }

    @Test
    void aMissingOrDamagedReleaseKeepsTheOldModule() throws IOException {
        Files.write(modules.resolve("RestModule-1.0.12.jar"), moduleJar("RestModule", "1.0.12"));

        new ModuleUpdater(modules, releases).update("1.0.14");
        assertEquals("1.0.12", installedVersion("RestModule-1.0.12.jar"));

        publish("restmodule", "RestModule", "1.0.14");
        files.put("download/v1.0.14/restmodule-1.0.14.jar.sha256", "0".repeat(64).getBytes(StandardCharsets.UTF_8));
        new ModuleUpdater(modules, releases).update("1.0.14");
        assertEquals("1.0.12", installedVersion("RestModule-1.0.12.jar"));
        try (var files = Files.list(modules)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void anOfficialModuleIsInstalledInTheMastersVersion() throws IOException {
        publish("restmodule", "RestModule", "1.0.14");
        var updater = new ModuleUpdater(modules, releases);
        var restModule = OfficialModule.byName("restmodule").orElseThrow();
        assertFalse(updater.isInstalled(restModule));

        var jar = updater.install(restModule, "1.0.14");

        assertEquals(modules.resolve("RestModule-1.0.14.jar"), jar);
        assertEquals("1.0.14", installedVersion("RestModule-1.0.14.jar"));
        assertTrue(updater.isInstalled(restModule));
    }

    @Test
    void installingFailsForDevelopmentBuildsAndMissingReleases() {
        var updater = new ModuleUpdater(modules, releases);
        var restModule = OfficialModule.byName("RestModule").orElseThrow();

        assertThrows(IOException.class, () -> updater.install(restModule, "UNKNOWN"));
        assertThrows(IOException.class, () -> updater.install(restModule, "1.0.99"));
        assertFalse(updater.isInstalled(restModule));
    }

    @Test
    void developmentBuildsDoNotUpdate() throws IOException {
        Files.write(modules.resolve("RestModule-1.0.12.jar"), moduleJar("RestModule", "1.0.12"));

        new ModuleUpdater(modules, releases).update("UNKNOWN");

        assertEquals("1.0.12", installedVersion("RestModule-1.0.12.jar"));
    }

}
