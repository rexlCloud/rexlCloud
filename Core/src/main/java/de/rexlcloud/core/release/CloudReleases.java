package de.rexlcloud.core.release;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * The jars of every rexlCloud version, attached to its GitHub release with the version in their name:
 * {@code launcher-<version>.jar}, {@code master-<version>.jar}, {@code wrapper-<version>.jar},
 * {@code bridge-<version>.jar}, {@code proxy-<version>.jar}, {@code restmodule-<version>.jar}, each with a
 * {@code .sha256} that is checked after the download.
 * <p>
 * The releases are read from {@value #DEFAULT_RELEASES}; the system property {@code rexlcloud.releases} points to
 * another repository's releases (e.g. a fork).
 */
public final class CloudReleases {

    public static final String DEFAULT_RELEASES = "https://github.com/rexlCloud/rexlCloud/releases";

    private final String releases;
    private final HttpClient httpClient = HttpClient.newBuilder()
            // GitHub sends release downloads to its storage with a redirect
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * @param releases URL of the releases page, e.g. {@value #DEFAULT_RELEASES}
     */
    public CloudReleases(String releases) {
        this.releases = releases.endsWith("/") ? releases.substring(0, releases.length() - 1) : releases;
    }

    /**
     * The official releases, or those set with the system property {@code rexlcloud.releases}.
     */
    public static CloudReleases official() {
        return new CloudReleases(System.getProperty("rexlcloud.releases", DEFAULT_RELEASES));
    }

    /**
     * @param name e.g. {@code bridge}
     * @return the URL of {@code <name>-<version>.jar} in the release of that version
     */
    public String jarUrl(String version, String name) {
        return releases + "/download/v" + version + "/" + name + "-" + version + ".jar";
    }

    /**
     * Downloads {@code <name>-<version>.jar} to {@code target}, checking its SHA-256. The file appears in one
     * step and only if it is complete; a failed download leaves {@code target} as it was.
     *
     * @throws IOException if the release has no such jar, the download fails or the checksum does not match
     */
    public void download(String version, String name, Path target) throws IOException {
        var url = jarUrl(version, name);
        var checksum = send(url + ".sha256", HttpResponse.BodyHandlers.ofString());
        if (checksum.statusCode() != 200) {
            throw new IOException("release " + version + " has no " + name + ".jar (HTTP " + checksum.statusCode() + ")");
        }
        // "<hash>" or "<hash>  <file name>" as sha256sum writes it
        var expected = checksum.body().trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        var folder = target.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        var temporary = Files.createTempFile(folder, name, ".download");
        try {
            var response = send(url, HttpResponse.BodyHandlers.ofFile(temporary));
            if (response.statusCode() != 200) {
                throw new IOException("the download of " + name + ".jar " + version + " failed with HTTP "
                        + response.statusCode());
            }
            if (!sha256(temporary).equals(expected)) {
                throw new IOException("the download of " + name + ".jar " + version + " is damaged (checksum mismatch)");
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static String sha256(Path file) throws IOException {
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

    private <T> HttpResponse<T> send(String url, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return httpClient.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).build(),
                    handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

}
