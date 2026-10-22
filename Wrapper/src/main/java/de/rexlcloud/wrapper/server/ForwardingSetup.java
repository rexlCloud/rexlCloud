package de.rexlcloud.wrapper.server;

import com.google.gson.JsonParser;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.server.ProxyForwarding;
import de.rexlcloud.core.server.ProxyForwarding.Mode;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Writes the proxy forwarding settings into the configuration files of a server before it starts, so players
 * keep their UUID behind the proxy and servers only accept players that come through a proxy.
 */
final class ForwardingSetup {

    static final String SECRET_FILE = "forwarding.secret";
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)");

    private ForwardingSetup() {
    }

    /**
     * Configures a Minecraft server (Paper/Spigot) behind the proxies.
     */
    static void configureServer(Path folder, String serverName, ProxyForwarding forwarding) throws IOException {
        setProperty(folder.resolve("server.properties"), "online-mode", "false");

        var jar = folder.resolve("server.jar");
        var version = minecraftVersion(jar).orElse(null);
        var mode = forwarding.mode();
        var paper = !isSpigot(jar);
        if (mode == Mode.MODERN && !paper) {
            Console.warn("%s runs Spigot, which has no modern forwarding. It uses legacy forwarding, which only works "
                    + "if forwardingMode in master.json is LEGACY", serverName);
            mode = Mode.LEGACY;
        } else if (mode == Mode.MODERN && version != null && !version.supportsModernForwarding()) {
            Console.warn("%s runs Minecraft %s, which has no modern forwarding. It uses legacy forwarding, which only "
                    + "works if forwardingMode in master.json is LEGACY", serverName, version);
            mode = Mode.LEGACY;
        }

        setYaml(folder.resolve("spigot.yml"), mode == Mode.LEGACY, "settings", "bungeecord");

        if (!paper) {
            return;
        }
        var modern = mode == Mode.MODERN;
        if (version == null || version.hasGlobalPaperConfig()) {
            var paperGlobal = folder.resolve("config").resolve("paper-global.yml");
            if (modern || Files.exists(paperGlobal)) {
                setYaml(paperGlobal, modern, "proxies", "velocity", "enabled");
                setYaml(paperGlobal, true, "proxies", "velocity", "online-mode");
                setYaml(paperGlobal, modern ? forwarding.secret() : "", "proxies", "velocity", "secret");
            }
        } else if (version.supportsModernForwarding()) {
            var paperYml = folder.resolve("paper.yml");
            if (modern || Files.exists(paperYml)) {
                setYaml(paperYml, modern, "settings", "velocity-support", "enabled");
                setYaml(paperYml, true, "settings", "velocity-support", "online-mode");
                setYaml(paperYml, modern ? forwarding.secret() : "", "settings", "velocity-support", "secret");
            }
        }
    }

    /**
     * Configures a proxy (Velocity or BungeeCord).
     */
    static void configureProxy(Path folder, String serverName, ProxyForwarding forwarding) throws IOException {
        var jar = folder.resolve("proxy.jar");
        if (!isVelocity(jar)) {
            if (forwarding.mode() == Mode.MODERN) {
                Console.warn("%s is a BungeeCord proxy, which only supports legacy forwarding. Set forwardingMode in "
                        + "master.json to LEGACY, or players cannot join the servers through it", serverName);
            }
            setYaml(folder.resolve("config.yml"), true, "ip_forward");
            return;
        }

        var velocityToml = folder.resolve("velocity.toml");
        setTomlKey(velocityToml, "player-info-forwarding-mode",
                '"' + (forwarding.mode() == Mode.MODERN ? "modern" : "legacy") + '"');
        if (implementationVersion(jar).filter(version -> version.startsWith("1.")).isPresent()) {
            // Velocity 1.x keeps the secret in the configuration itself
            setTomlKey(velocityToml, "forwarding-secret", '"' + forwarding.secret() + '"');
        } else {
            setTomlKey(velocityToml, "forwarding-secret-file", '"' + SECRET_FILE + '"');
            Files.writeString(folder.resolve(SECRET_FILE), forwarding.secret(), StandardCharsets.UTF_8);
        }
    }

    /**
     * BungeeCord takes its port from config.yml, not from the command line: the first listener gets the server's
     * port. Without a config.yml a minimal one is written; the servers come from the cloud plugin, not from it.
     */
    @SuppressWarnings("unchecked")
    static void configureBungeeCordListener(Path folder, int port) throws IOException {
        var file = folder.resolve("config.yml");
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        var yaml = new Yaml(options);
        Map<String, Object> root = null;
        if (Files.exists(file)) {
            var loaded = yaml.load(new StringReader(Files.readString(file, StandardCharsets.UTF_8)));
            if (loaded instanceof Map<?, ?> map) {
                root = (Map<String, Object>) map;
            }
        }
        if (root == null) {
            root = new LinkedHashMap<>();
            root.put("online_mode", true);
            root.put("ip_forward", true);
            root.put("servers", new LinkedHashMap<>());
        }
        Map<String, Object> listener;
        if (root.get("listeners") instanceof List<?> listeners && !listeners.isEmpty()
                && listeners.getFirst() instanceof Map<?, ?> first) {
            listener = (Map<String, Object>) first;
        } else {
            listener = new LinkedHashMap<>();
            listener.put("motd", "A rexlCloud network");
            listener.put("max_players", 100);
            listener.put("priorities", new ArrayList<>());
            listener.put("force_default_server", false);
            listener.put("forced_hosts", new LinkedHashMap<>());
            listener.put("tab_list", "GLOBAL_PING");
            listener.put("ping_passthrough", false);
            listener.put("query_enabled", false);
            root.put("listeners", new ArrayList<>(List.of(listener)));
        }
        listener.put("host", "0.0.0.0:" + port);
        listener.put("query_port", port);
        Files.writeString(file, yaml.dump(root), StandardCharsets.UTF_8);
    }

    record MinecraftVersion(int major, int minor) {

        static Optional<MinecraftVersion> parse(@Nullable String version) {
            if (version == null) {
                return Optional.empty();
            }
            var matcher = VERSION.matcher(version.trim());
            if (!matcher.find()) {
                return Optional.empty();
            }
            return Optional.of(new MinecraftVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
        }

        /**
         * Paper has modern forwarding since 1.13.
         */
        boolean supportsModernForwarding() {
            return major > 1 || minor >= 13;
        }

        /**
         * Paper moved its settings from paper.yml to config/paper-global.yml in 1.19.
         */
        boolean hasGlobalPaperConfig() {
            return major > 1 || minor >= 19;
        }

        @Override
        public String toString() {
            return major + "." + minor;
        }
    }

    /**
     * Minecraft version of a Paper/Spigot jar, read from the metadata Paperclip and Mojang put into it.
     */
    static Optional<MinecraftVersion> minecraftVersion(Path jar) {
        if (!Files.isRegularFile(jar)) {
            return Optional.empty();
        }
        try (var jarFile = new JarFile(jar.toFile())) {
            var versionJson = jarFile.getEntry("version.json");
            if (versionJson != null) {
                try (var in = jarFile.getInputStream(versionJson)) {
                    var json = JsonParser.parseString(read(in)).getAsJsonObject();
                    var id = json.has("id") ? json.get("id").getAsString() : null;
                    return MinecraftVersion.parse(id);
                }
            }
            // Paperclip before 1.17
            var patchProperties = jarFile.getEntry("patch.properties");
            if (patchProperties != null) {
                try (var in = jarFile.getInputStream(patchProperties)) {
                    var properties = new Properties();
                    properties.load(in);
                    return MinecraftVersion.parse(properties.getProperty("version"));
                }
            }
            var patchJson = jarFile.getEntry("patch.json");
            if (patchJson != null) {
                try (var in = jarFile.getInputStream(patchJson)) {
                    var json = JsonParser.parseString(read(in)).getAsJsonObject();
                    return MinecraftVersion.parse(json.has("version") ? json.get("version").getAsString() : null);
                }
            }
        } catch (IOException | RuntimeException e) {
            Console.debug("Could not read the Minecraft version of %s: %s", jar, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Whether the jar is Spigot (or CraftBukkit) rather than Paper or a Paper fork, which ship as Paperclip.
     */
    static boolean isSpigot(Path jar) {
        if (!Files.isRegularFile(jar)) {
            return false;
        }
        try (var jarFile = new JarFile(jar.toFile())) {
            var craftBukkit = false;
            for (var entries = jarFile.entries(); entries.hasMoreElements(); ) {
                var name = entries.nextElement().getName();
                if (name.startsWith("io/papermc/") || name.startsWith("com/destroystokyo/")) {
                    return false;
                }
                craftBukkit |= name.startsWith("org/bukkit/craftbukkit/");
            }
            return craftBukkit;
        } catch (IOException e) {
            return false;
        }
    }

    static boolean isVelocity(Path jar) {
        if (!Files.isRegularFile(jar)) {
            return true;
        }
        try (var jarFile = new JarFile(jar.toFile())) {
            return jarFile.getEntry("com/velocitypowered/proxy/Velocity.class") != null;
        } catch (IOException e) {
            return true;
        }
    }

    private static Optional<String> implementationVersion(Path jar) {
        try (var jarFile = new JarFile(jar.toFile())) {
            var manifest = jarFile.getManifest();
            return Optional.ofNullable(manifest == null ? null
                    : manifest.getMainAttributes().getValue("Implementation-Version"));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String read(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Sets a value in a YAML file, creating the file and missing sections. The server adds its defaults for
     * everything else on start.
     */
    @SuppressWarnings("unchecked")
    static void setYaml(Path file, Object value, String... path) throws IOException {
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        var yaml = new Yaml(options);

        Map<String, Object> root = null;
        if (Files.exists(file)) {
            var loaded = yaml.load(new StringReader(Files.readString(file, StandardCharsets.UTF_8)));
            if (loaded instanceof Map<?, ?> map) {
                root = (Map<String, Object>) map;
            }
        }
        if (root == null) {
            root = new LinkedHashMap<>();
        }
        var node = root;
        for (int i = 0; i < path.length - 1; i++) {
            var child = node.get(path[i]);
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                node.put(path[i], child);
            }
            node = (Map<String, Object>) child;
        }
        node.put(path[path.length - 1], value);

        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, yaml.dump(root), StandardCharsets.UTF_8);
    }

    /**
     * Sets a top-level key of a TOML file. Comments and the other settings are kept.
     *
     * @param value TOML value, quoted if it is a string
     */
    static void setTomlKey(Path file, String key, String value) throws IOException {
        var lines = Files.exists(file) ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                : new ArrayList<String>();
        var line = key + " = " + value;
        // Top-level keys must come before the first [table]
        var firstTable = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            var trimmed = lines.get(i).trim();
            if (trimmed.startsWith("[")) {
                firstTable = i;
                break;
            }
            if (trimmed.startsWith(key) && trimmed.substring(key.length()).trim().startsWith("=")) {
                lines.set(i, line);
                Files.write(file, lines, StandardCharsets.UTF_8);
                return;
            }
        }
        lines.add(firstTable, line);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /**
     * Sets a key of a .properties file only if it is not set yet, e.g. by the template.
     */
    static void setPropertyIfAbsent(Path file, String key, String value) throws IOException {
        if (Files.exists(file)) {
            for (var line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.startsWith(key + "=") || line.startsWith(key + " ")) {
                    return;
                }
            }
        }
        setProperty(file, key, value);
    }

    /**
     * Sets a key of a .properties file, keeping the order and comments of the other lines.
     */
    static void setProperty(Path file, String key, String value) throws IOException {
        var lines = Files.exists(file) ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                : new ArrayList<String>();
        var line = key + "=" + value;
        for (int i = 0; i < lines.size(); i++) {
            var current = lines.get(i);
            if (current.startsWith(key + "=") || current.startsWith(key + " ")) {
                lines.set(i, line);
                Files.write(file, lines, StandardCharsets.UTF_8);
                return;
            }
        }
        lines.add(line);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

}
