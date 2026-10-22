package de.rexlcloud.core.server.files;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.server.ServerType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class ServerJarManager {

    private static final String SPIGOT_VERSIONS = "https://hub.spigotmc.org/versions/";
    private static final String BUNGEECORD_JENKINS = "https://hub.spigotmc.org/jenkins/job/BungeeCord/";
    /**
     * Release versions only, e.g. 1.21.4.json but not 1.13-pre7.json.
     */
    private static final Pattern SPIGOT_VERSION_LINK = Pattern.compile("href=\"(\\d+(?:\\.\\d+)+)\\.json\"");

    /**
     * One client for all requests, with timeouts: a download site that does not answer must not hold up the cloud.
     */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Filled in the background while the master starts; read from other threads.
     */
    private final List<ServerJar> serverJars = new CopyOnWriteArrayList<>();

    /**
     * Starts loading the versions of Paper, Spigot, Velocity and BungeeCord in the background; until they are
     * loaded only the custom jar is offered.
     */
    public ServerJarManager() {
        serverJars.add(ServerJar.custom());
        Thread.ofVirtual().name("server-jar-list").start(() -> {
            loadPaperJars();
            loadSpigotJars();
            loadVelocityJars();
            loadBungeeCordJar();
            Console.info("Loaded %s server versions to install", serverJars.size() - 1);
        });
    }

    private static HttpResponse<String> get(String url) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void warn(String source, Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        var message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
        Console.warn("Could not load the %s versions: %s", source, message);
    }

    public boolean hasServerJar(String fullText) {
        return serverJars.stream().anyMatch(serverJar -> serverJar.getFullText().equals(fullText));
    }

    public ServerJar getServerJar(String fullText) {
        return serverJars.stream().filter(serverJar -> serverJar.getFullText().equals(fullText)).findAny().orElseThrow();
    }

    public boolean addServerJar(ServerJar serverJar) {
        if (!serverJar.checkAll()) {
            return false;
        }

        serverJars.add(serverJar);

        return true;
    }

    public void removeServerJar(ServerJar serverJar) {
        serverJars.removeIf(serverJar1 -> serverJar1.equals(serverJar));
    }

    /**
     * The jars that can be installed; right after the start only the custom jar, the others follow within seconds.
     */
    public ArrayList<ServerJar> getServerJars() {
        return new ArrayList<>(serverJars);
    }

    public void reloadBungeeCordJars() {
        serverJars.removeIf(serverJar -> serverJar.name.equals("BungeeCord"));
        loadBungeeCordJar();
    }

    /**
     * BungeeCord has no versioned releases: the newest successful build of SpigotMC's Jenkins, by its build number.
     */
    private void loadBungeeCordJar() {
        try {
            var response = get(BUNGEECORD_JENKINS + "lastSuccessfulBuild/api/json?tree=number");
            var build = JsonParser.parseString(response.body()).getAsJsonObject().get("number").getAsInt();
            addServerJar(new ServerJar("BungeeCord", null, String.valueOf(build), ServerType.PROXY,
                    BUNGEECORD_JENKINS + build + "/artifact/bootstrap/target/BungeeCord.jar"));
        } catch (Exception e) {
            warn("BungeeCord", e);
        }
    }

    public void reloadPaperJars() {
        serverJars.removeIf(serverJar -> serverJar.name.equals("Paper"));
        loadPaperJars();
    }

    public void reloadSpigotJars() {
        serverJars.removeIf(serverJar -> serverJar.name.equals("Spigot"));
        loadSpigotJars();
    }

    public void reloadVelocityJars() {
        serverJars.removeIf(serverJar -> serverJar.name.equals("Velocity"));
        loadVelocityJars();
    }

    private void loadPaperJars() {
        try {
            var response = get("https://fill.papermc.io/v3/projects/paper/versions");
            JsonObject jsonTree = JsonParser.parseString(response.body()).getAsJsonObject();
            jsonTree.get("versions").getAsJsonArray().forEach(versionElement -> {
                JsonObject versionObject = versionElement.getAsJsonObject();
                String versionName = versionObject.get("version").getAsJsonObject().get("id").getAsString();
                if (!versionName.contains("-")) {
                    loadPaperVersion(versionName);
                }
            });
        } catch (Exception e) {
            warn("Paper", e);
        }
    }

    private void loadPaperVersion(String minecraftVersion) {
        try {
            var response = get("https://fill.papermc.io/v3/projects/paper/versions/" + minecraftVersion + "/builds/latest");
            var download = getDownloadUrl(response);
            if (download == null) {
                return;
            }
            ServerJar serverJar = new ServerJar("Paper", minecraftVersion, ServerType.SERVER, download);
            addServerJar(serverJar);
        } catch (Exception e) {
            Console.debug("Could not load Paper %s: %s", minecraftVersion, e);
        }
    }

    /**
     * Spigot versions that BuildTools can build with the Java version the cloud runs on. Each version's descriptor
     * names the Java versions it accepts (as class file versions); versions without one need Java 8.
     */
    private void loadSpigotJars() {
        var javaClassVersion = Runtime.version().feature() + 44;
        try {
            var index = get(SPIGOT_VERSIONS);
            if (index.statusCode() != 200) {
                return;
            }
            var versions = new TreeSet<String>(Comparator.comparing(ServerJarManager::versionKey).reversed());
            var matcher = SPIGOT_VERSION_LINK.matcher(index.body());
            while (matcher.find()) {
                versions.add(matcher.group(1));
            }
            var descriptors = versions.stream().map(version -> HTTP
                    .sendAsync(HttpRequest.newBuilder(URI.create(SPIGOT_VERSIONS + version + ".json"))
                                    .timeout(REQUEST_TIMEOUT).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> response.statusCode() == 200
                            && buildableWith(response.body(), javaClassVersion) ? version : null)
                    .exceptionally(e -> null)).toList();
            for (var descriptor : descriptors) {
                var version = descriptor.join();
                if (version != null) {
                    addServerJar(new ServerJar("Spigot", version, null, ServerType.SERVER,
                            SPIGOT_VERSIONS + version + ".json", ServerJar.Source.SPIGOT_BUILD_TOOLS));
                }
            }
        } catch (Exception e) {
            warn("Spigot", e);
        }
    }

    /**
     * @param descriptor JSON of a version from hub.spigotmc.org/versions
     */
    static boolean buildableWith(String descriptorJson, int javaClassVersion) {
        var descriptor = JsonParser.parseString(descriptorJson).getAsJsonObject();
        if (!(descriptor.get("javaVersions") instanceof JsonArray range) || range.size() != 2) {
            return javaClassVersion == 52;
        }
        return range.get(0).getAsInt() <= javaClassVersion && javaClassVersion <= range.get(1).getAsInt();
    }

    /**
     * "1.21.10" sorts after "1.21.9".
     */
    static String versionKey(String version) {
        var key = new StringBuilder();
        for (var part : version.split("\\.")) {
            key.append(String.format("%05d", Integer.parseInt(part)));
        }
        return key.toString();
    }

    private void loadVelocityJars() {
        try {
            var response = get("https://fill.papermc.io/v3/projects/velocity/versions");
            JsonObject jsonTree = JsonParser.parseString(response.body()).getAsJsonObject();
            jsonTree.get("versions").getAsJsonArray().forEach(versionElement -> {
                JsonObject versionObject = versionElement.getAsJsonObject();
                String versionName = versionObject.get("version").getAsJsonObject().get("id").getAsString();
                loadVelocityVersion(versionName);
            });
        } catch (Exception e) {
            warn("Velocity", e);
        }
    }

    private void loadVelocityVersion(String velocityVersion) {
        try {
            var response = get("https://fill.papermc.io/v3/projects/velocity/versions/" + velocityVersion + "/builds/latest");
            var download = getDownloadUrl(response);
            if (download == null) {
                return;
            }
            ServerJar serverJar = new ServerJar("Velocity", null, velocityVersion, ServerType.PROXY, download);
            addServerJar(serverJar);
        } catch (Exception e) {
            Console.debug("Could not load Velocity %s: %s", velocityVersion, e);
        }
    }

    /**
     * Download URL of a "builds/latest" response, or {@code null} for versions without builds.
     */
    private static String getDownloadUrl(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            return null;
        }
        JsonElement jsonTree = JsonParser.parseString(response.body());
        if (!jsonTree.isJsonObject() || !(jsonTree.getAsJsonObject().get("downloads") instanceof JsonObject downloads)
                || !(downloads.get("server:default") instanceof JsonObject serverDownload)) {
            return null;
        }
        return serverDownload.get("url").getAsString();
    }

}
