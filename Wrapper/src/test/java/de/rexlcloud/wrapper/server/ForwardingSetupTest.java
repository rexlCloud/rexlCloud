package de.rexlcloud.wrapper.server;

import de.rexlcloud.core.server.ProxyForwarding;
import de.rexlcloud.core.server.ProxyForwarding.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class ForwardingSetupTest {

    private static final ProxyForwarding MODERN = new ProxyForwarding(Mode.MODERN, "s3cret");
    private static final ProxyForwarding LEGACY = new ProxyForwarding(Mode.LEGACY, "s3cret");

    @TempDir
    Path folder;

    /**
     * Writes a jar with the given entries (name, content) and an optional Implementation-Version.
     */
    private static void jar(Path file, String implementationVersion, String... entries) throws IOException {
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (implementationVersion != null) {
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, implementationVersion);
        }
        try (var out = new JarOutputStream(Files.newOutputStream(file), manifest)) {
            for (int i = 0; i < entries.length; i += 2) {
                out.putNextEntry(new JarEntry(entries[i]));
                out.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    private static void paper(Path folder, String version) throws IOException {
        jar(folder.resolve("server.jar"), null, "version.json", "{\"id\": \"" + version + "\", \"name\": \"" + version + "\"}");
    }

    @SuppressWarnings("unchecked")
    private static Object yaml(Path file, String... path) throws IOException {
        Object node = new Yaml().load(Files.readString(file));
        for (var key : path) {
            node = ((Map<String, Object>) node).get(key);
        }
        return node;
    }

    @Test
    void modernServerEnablesVelocityForwarding() throws IOException {
        paper(folder, "26.3");

        ForwardingSetup.configureServer(folder, "Lobby-1", MODERN);

        assertTrue(Files.readAllLines(folder.resolve("server.properties")).contains("online-mode=false"));
        assertEquals(false, yaml(folder.resolve("spigot.yml"), "settings", "bungeecord"));
        var paperGlobal = folder.resolve("config/paper-global.yml");
        assertEquals(true, yaml(paperGlobal, "proxies", "velocity", "enabled"));
        assertEquals(true, yaml(paperGlobal, "proxies", "velocity", "online-mode"));
        assertEquals("s3cret", yaml(paperGlobal, "proxies", "velocity", "secret"));
    }

    @Test
    void existingFilesKeepTheirOtherSettings() throws IOException {
        paper(folder, "1.21.4");
        Files.writeString(folder.resolve("server.properties"), "# comment\nonline-mode=true\nmotd=Hi\n");
        Files.createDirectories(folder.resolve("config"));
        Files.writeString(folder.resolve("config/paper-global.yml"),
                "_version: 29\nproxies:\n  velocity:\n    enabled: false\n    secret: ''\nchunk-system:\n  io-threads: 4\n");

        ForwardingSetup.configureServer(folder, "Lobby-1", MODERN);

        assertEquals(List.of("# comment", "online-mode=false", "motd=Hi"), Files.readAllLines(folder.resolve("server.properties")));
        var paperGlobal = folder.resolve("config/paper-global.yml");
        assertEquals(29, yaml(paperGlobal, "_version"));
        assertEquals(4, yaml(paperGlobal, "chunk-system", "io-threads"));
        assertEquals("s3cret", yaml(paperGlobal, "proxies", "velocity", "secret"));
    }

    @Test
    void legacyServerUsesBungeeCordForwarding() throws IOException {
        paper(folder, "26.3");

        ForwardingSetup.configureServer(folder, "Lobby-1", LEGACY);

        assertEquals(true, yaml(folder.resolve("spigot.yml"), "settings", "bungeecord"));
        assertFalse(Files.exists(folder.resolve("config/paper-global.yml")));
    }

    @Test
    void paperBefore119UsesPaperYml() throws IOException {
        paper(folder, "1.16.5");

        ForwardingSetup.configureServer(folder, "Old-1", MODERN);

        assertEquals(true, yaml(folder.resolve("paper.yml"), "settings", "velocity-support", "enabled"));
        assertEquals("s3cret", yaml(folder.resolve("paper.yml"), "settings", "velocity-support", "secret"));
        assertFalse(Files.exists(folder.resolve("config/paper-global.yml")));
    }

    @Test
    void serversWithoutModernForwardingFallBackToLegacy() throws IOException {
        // Paperclip before 1.17 has a patch.properties
        jar(folder.resolve("server.jar"), null, "patch.properties", "version=1.8.8\n");

        ForwardingSetup.configureServer(folder, "Legacy-1", MODERN);

        assertEquals(true, yaml(folder.resolve("spigot.yml"), "settings", "bungeecord"));
        assertFalse(Files.exists(folder.resolve("paper.yml")));
    }

    @Test
    void spigotUsesLegacyForwardingAndNoPaperConfig() throws IOException {
        // Spigot 1.18+ jars are bundlers with a version.json, like Paperclip, but CraftBukkit classes instead
        jar(folder.resolve("server.jar"), null, "version.json", "{\"id\": \"26.3\"}",
                "org/bukkit/craftbukkit/bootstrap/Main.class", "");

        ForwardingSetup.configureServer(folder, "Spigot-1", MODERN);

        assertEquals(true, yaml(folder.resolve("spigot.yml"), "settings", "bungeecord"));
        assertFalse(Files.exists(folder.resolve("config/paper-global.yml")));
        assertFalse(Files.exists(folder.resolve("paper.yml")));
    }

    @Test
    void paperclipIsNotSpigot() throws IOException {
        jar(folder.resolve("server.jar"), null, "io/papermc/paperclip/Main.class", "");
        assertFalse(ForwardingSetup.isSpigot(folder.resolve("server.jar")));
    }

    @Test
    void readsTheMinecraftVersion() throws IOException {
        paper(folder, "1.21.11");
        assertEquals(new ForwardingSetup.MinecraftVersion(1, 21),
                ForwardingSetup.minecraftVersion(folder.resolve("server.jar")).orElseThrow());
        assertTrue(ForwardingSetup.minecraftVersion(folder.resolve("missing.jar")).isEmpty());
    }

    @Test
    void velocityGetsModernForwardingAndTheSecretFile() throws IOException {
        jar(folder.resolve("proxy.jar"), "3.4.0", "com/velocitypowered/proxy/Velocity.class", "");
        Files.writeString(folder.resolve("velocity.toml"), """
                config-version = "2.7"
                bind = "0.0.0.0:25565"
                player-info-forwarding-mode = "NONE"

                [servers]
                lobby = "127.0.0.1:30066"
                """);

        ForwardingSetup.configureProxy(folder, "Proxy-1", MODERN);

        var lines = Files.readAllLines(folder.resolve("velocity.toml"));
        assertTrue(lines.contains("player-info-forwarding-mode = \"modern\""), lines.toString());
        var secretLine = lines.indexOf("forwarding-secret-file = \"forwarding.secret\"");
        assertTrue(secretLine >= 0 && secretLine < lines.indexOf("[servers]"), "top-level key before the first table");
        assertEquals("s3cret", Files.readString(folder.resolve("forwarding.secret")));
    }

    @Test
    void velocityWithoutConfigurationGetsAMinimalOne() throws IOException {
        jar(folder.resolve("proxy.jar"), "3.4.0", "com/velocitypowered/proxy/Velocity.class", "");

        ForwardingSetup.configureProxy(folder, "Proxy-1", LEGACY);

        assertEquals(List.of("player-info-forwarding-mode = \"legacy\"", "forwarding-secret-file = \"forwarding.secret\""),
                Files.readAllLines(folder.resolve("velocity.toml")));
    }

    @Test
    void bungeeCordGetsIpForward() throws IOException {
        jar(folder.resolve("proxy.jar"), "git:BungeeCord", "net/md_5/bungee/BungeeCord.class", "");
        Files.writeString(folder.resolve("config.yml"), "ip_forward: false\nlisteners:\n- host: 0.0.0.0:25577\n");

        ForwardingSetup.configureProxy(folder, "Proxy-1", LEGACY);

        assertEquals(true, yaml(folder.resolve("config.yml"), "ip_forward"));
        assertNotNull(yaml(folder.resolve("config.yml"), "listeners"));
        assertFalse(Files.exists(folder.resolve("velocity.toml")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bungeeCordListensOnTheServersPort() throws IOException {
        Files.writeString(folder.resolve("config.yml"), """
                ip_forward: true
                timeout: 30000
                listeners:
                - host: 0.0.0.0:25577
                  query_port: 25577
                  motd: Hello
                """);

        ForwardingSetup.configureBungeeCordListener(folder, 25566);

        var listener = ((List<Map<String, Object>>) yaml(folder.resolve("config.yml"), "listeners")).getFirst();
        assertEquals("0.0.0.0:25566", listener.get("host"));
        assertEquals(25566, listener.get("query_port"));
        assertEquals("Hello", listener.get("motd"), "the rest of the listener stays");
        assertEquals(30000, yaml(folder.resolve("config.yml"), "timeout"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bungeeCordWithoutConfigurationGetsAMinimalOne() throws IOException {
        ForwardingSetup.configureBungeeCordListener(folder, 25570);

        var config = folder.resolve("config.yml");
        assertEquals(true, yaml(config, "ip_forward"));
        assertEquals(true, yaml(config, "online_mode"));
        assertEquals(Map.of(), yaml(config, "servers"), "the cloud plugin adds the servers");
        var listener = ((List<Map<String, Object>>) yaml(config, "listeners")).getFirst();
        assertEquals("0.0.0.0:25570", listener.get("host"));
    }

}
