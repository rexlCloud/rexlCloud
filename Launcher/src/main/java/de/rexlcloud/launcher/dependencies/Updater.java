package de.rexlcloud.launcher.dependencies;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Downloads the master or wrapper jar from the cloud's GitHub releases and keeps it up to date. Every release has
 * {@code master.jar}, {@code wrapper.jar} and {@code launcher.jar}, each with a {@code .sha256} that is checked.
 * <p>
 * {@code launcher.cnf} holds {@code auto_update} and the installed {@code master_version}/{@code wrapper_version}.
 * With auto_update the newest version is installed on every start; without it the version in the file is used.
 * If GitHub cannot be reached, an installed jar is started anyway. The system property {@code rexlcloud.releases}
 * points to another repository's releases (e.g. a fork).
 */
public class Updater {

    private static final String DEFAULT_RELEASES = "https://github.com/rexlCloud/rexlCloud/releases";
    private static final String AUTO_UPDATE = "auto_update";

    private final Path folder;
    private final String releases;
    private final Path configFile;
    private final Map<String, String> config = new LinkedHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    /**
     * Only for {@code releases/latest}: its redirect names the newest version.
     */
    private final HttpClient noRedirects = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private String newestVersion;

    public Updater() throws IOException {
        this(Path.of(""), System.getProperty("rexlcloud.releases", DEFAULT_RELEASES));
    }

    /**
     * @param folder   working folder with launcher.cnf and libs/
     * @param releases URL of the releases page, e.g. https://github.com/rexlCloud/rexlCloud/releases
     */
    public Updater(Path folder, String releases) throws IOException {
        this.folder = folder;
        this.releases = releases.endsWith("/") ? releases.substring(0, releases.length() - 1) : releases;
        this.configFile = folder.resolve("launcher.cnf");
        Files.createDirectories(folder.resolve("libs"));
        readConfig();
        config.putIfAbsent(AUTO_UPDATE, "true");
        saveConfig();
    }

    private void readConfig() throws IOException {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        for (var line : Files.readAllLines(configFile, StandardCharsets.UTF_8)) {
            var separator = line.indexOf('=');
            if (line.isBlank() || line.startsWith("#") || separator <= 0) {
                continue;
            }
            config.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        // Written by older launchers instead of master_version/wrapper_version
        config.remove("version");
    }

    private void saveConfig() throws IOException {
        var lines = config.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toList();
        Files.write(configFile, lines, StandardCharsets.UTF_8);
    }

    public boolean shouldUpdate() {
        return !"false".equalsIgnoreCase(config.get(AUTO_UPDATE));
    }

    /**
     * Version to start: the newest with auto_update, otherwise the installed one. Saved in launcher.cnf.
     *
     * @throws IOException if no version is known and GitHub cannot be reached
     */
    public String getVersion(String type) throws IOException {
        var key = type + "_version";
        var installed = config.get(key);
        if (installed != null && !installed.isBlank() && !shouldUpdate()) {
            return installed;
        }
        String newest;
        try {
            newest = getNewestVersion(type);
        } catch (IOException e) {
            if (installed != null && !installed.isBlank() && Files.isRegularFile(jar(type))) {
                System.err.println("Could not check for updates (" + e.getMessage() + "), starting " + type + " " + installed);
                return installed;
            }
            throw e;
        }
        if (!newest.equals(installed)) {
            config.put(key, newest);
            saveConfig();
        }
        return newest;
    }

    /**
     * The version of the newest release (master, wrapper and launcher are released together). GitHub answers
     * {@code releases/latest} with a redirect to {@code releases/tag/v<version>}; asked once per start.
     *
     * @param type kept for the callers; every jar has the release's version
     */
    public String getNewestVersion(String type) throws IOException {
        if (newestVersion != null) {
            return newestVersion;
        }
        HttpResponse<Void> response;
        try {
            response = noRedirects.send(HttpRequest.newBuilder(URI.create(releases + "/latest"))
                    .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while looking for the newest release", e);
        }
        var location = response.headers().firstValue("location").orElse("");
        var tag = location.substring(location.lastIndexOf('/') + 1);
        if (response.statusCode() / 100 != 3 || !location.contains("/tag/") || tag.isBlank()) {
            throw new IOException("No release found at " + releases + " (HTTP " + response.statusCode() + ")");
        }
        newestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
        return newestVersion;
    }

    private String getDownloadUrl(String version, String type) {
        return releases + "/download/v" + version + "/" + type + ".jar";
    }

    private Path jar(String type) {
        return folder.resolve("libs").resolve(type + ".jar");
    }

    /**
     * Makes sure libs/&lt;type&gt;.jar is the version to start. A failed download keeps the old jar.
     */
    public void download(String type) throws IOException {
        var file = jar(type);
        if (Files.isRegularFile(file) && !shouldUpdate() && config.containsKey(type + "_version")) {
            return;
        }
        var version = getVersion(type);
        String expectedSha256;
        try {
            expectedSha256 = checksum(getDownloadUrl(version, type));
        } catch (IOException e) {
            if (Files.isRegularFile(file)) {
                System.err.println("Could not check " + type + ".jar (" + e.getMessage() + "), starting the installed one");
                return;
            }
            throw e;
        }
        if (Files.isRegularFile(file) && getSha256(file).equals(expectedSha256)) {
            return;
        }

        System.out.println("Downloading " + type + " " + version + "...");
        var temporary = Files.createTempFile(file.getParent(), type, ".download");
        try {
            var response = send(getDownloadUrl(version, type), HttpResponse.BodyHandlers.ofFile(temporary));
            if (response.statusCode() != 200) {
                throw new IOException("Downloading " + type + " " + version + " failed with HTTP " + response.statusCode());
            }
            if (!getSha256(temporary).equals(expectedSha256)) {
                throw new IOException("The downloaded " + type + ".jar is damaged (checksum mismatch)");
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Replaces the running launcher's jar with the newest release, if auto_update is on and there is a newer one.
     * The running launcher goes on as it is; the new one starts next time. Where the jar cannot be replaced while it
     * runs (Windows), the new one is left next to it as {@code <name>.new}.
     *
     * @param ownJar     the running launcher's jar
     * @param ownVersion its version, {@code null} for development builds, which never update themselves
     * @return the version that was installed, or {@code null}
     */
    public String updateLauncher(Path ownJar, String ownVersion) {
        if (!shouldUpdate() || ownVersion == null || !Files.isRegularFile(ownJar)) {
            return null;
        }
        try {
            var newest = getNewestVersion("launcher");
            if (newest.equals(ownVersion)) {
                return null;
            }
            var url = getDownloadUrl(newest, "launcher");
            var expectedSha256 = checksum(url);
            var temporary = Files.createTempFile(ownJar.toAbsolutePath().getParent(), "launcher", ".download");
            try {
                var response = send(url, HttpResponse.BodyHandlers.ofFile(temporary));
                if (response.statusCode() != 200) {
                    throw new IOException("Downloading the launcher " + newest + " failed with HTTP " + response.statusCode());
                }
                if (!getSha256(temporary).equals(expectedSha256)) {
                    throw new IOException("The downloaded launcher is damaged (checksum mismatch)");
                }
                try {
                    Files.move(temporary, ownJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    System.out.println("Updated the launcher from " + ownVersion + " to " + newest + "; it is used from the next start");
                } catch (IOException e) {
                    var next = ownJar.resolveSibling(ownJar.getFileName() + ".new");
                    Files.move(temporary, next, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Launcher " + newest + " downloaded to " + next.getFileName()
                            + "; replace " + ownJar.getFileName() + " with it while the cloud is stopped");
                }
                return newest;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            System.err.println("Could not update the launcher: " + e.getMessage());
            return null;
        }
    }

    private String getText(String url) throws IOException {
        var response = send(url, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(url + " answered with HTTP " + response.statusCode());
        }
        return response.body();
    }

    private <T> HttpResponse<T> send(String url, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return httpClient.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).build(), handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while loading " + url, e);
        }
    }

    /**
     * The SHA-256 next to a release jar: "&lt;hash&gt;" or "&lt;hash&gt;  &lt;file&gt;" as sha256sum writes it.
     */
    private String checksum(String jarUrl) throws IOException {
        return getText(jarUrl + ".sha256").trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }

    static String getSha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void loadMaster(ClassLoader classLoader, List<String> args) throws Exception {
        args.add("--version=" + getVersion("master"));
        try {
            new CustomClassLoader(jar("master").toFile(), classLoader, "de.rexlcloud.master.RexlCloudMaster", args.toArray(String[]::new));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadWrapper(ClassLoader classLoader, List<String> args) throws Exception {
        args.add("--version=" + getVersion("wrapper"));
        try {
            new CustomClassLoader(jar("wrapper").toFile(), classLoader, "de.rexlcloud.wrapper.RexlCloudWrapper", args.toArray(String[]::new));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
