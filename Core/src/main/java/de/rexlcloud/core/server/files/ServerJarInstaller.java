package de.rexlcloud.core.server.files;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.server.template.Templates;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Puts the jar of a {@link ServerJar} into place: downloads it, or builds Spigot with SpigotMC's BuildTools.
 */
public final class ServerJarInstaller {

    static final String BUILD_TOOLS_URL =
            "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar";
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(30);
    /**
     * BuildTools works in one shared folder, so only one build runs at a time.
     */
    private static final ReentrantLock BUILD_LOCK = new ReentrantLock();

    private final Path cacheFolder;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * @param cacheFolder keeps BuildTools, its work folder and the built jars between installs
     */
    public ServerJarInstaller(Path cacheFolder) {
        this.cacheFolder = cacheFolder;
    }

    /**
     * Installer that caches in {@code cache/} next to the master or wrapper.
     */
    public static ServerJarInstaller inWorkingDirectory() {
        return new ServerJarInstaller(Path.of("cache"));
    }

    /**
     * Writes the jar to {@code target}. Building Spigot takes a few minutes the first time per version.
     *
     * @throws IOException if the jar could not be downloaded or built; {@code target} is then left unchanged
     */
    public void install(ServerJar serverJar, Path target) throws IOException {
        var parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        switch (serverJar.getSource()) {
            case DOWNLOAD -> download(serverJar.getDownloadUrl(), target);
            case SPIGOT_BUILD_TOOLS -> Files.copy(buildSpigot(serverJar.getMinecraftVersion()), target,
                    StandardCopyOption.REPLACE_EXISTING);
            case CUSTOM -> throw new IOException("A custom jar is not installed, put it into the template as "
                    + target.getFileName());
        }
    }

    /**
     * Downloads to a temporary file first, so a failed download does not leave a broken jar behind. Its name keeps
     * the template sync from sending it while it is incomplete.
     */
    private void download(String url, Path target) throws IOException {
        var temporary = Files.createTempFile(target.toAbsolutePath().getParent(),
                Templates.TEMPORARY_PREFIX + "download-", ".tmp");
        try {
            var response = httpClient.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofFile(temporary));
            if (response.statusCode() != 200) {
                throw new IOException("Downloading " + url + " failed with HTTP " + response.statusCode());
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Builds Spigot for the Minecraft version, or returns the jar built before.
     */
    Path buildSpigot(String minecraftVersion) throws IOException {
        if (!minecraftVersion.matches("\\d+(\\.\\d+)+")) {
            throw new IOException("Invalid Minecraft version " + minecraftVersion);
        }
        var jarsFolder = cacheFolder.resolve("spigot");
        var jar = jarsFolder.resolve("spigot-" + minecraftVersion + ".jar");
        BUILD_LOCK.lock();
        try {
            if (Files.isRegularFile(jar)) {
                return jar;
            }
            checkGit();
            var workFolder = Files.createDirectories(cacheFolder.resolve("buildtools"));
            Files.createDirectories(jarsFolder);
            var buildTools = workFolder.resolve("BuildTools.jar");
            download(BUILD_TOOLS_URL, buildTools);

            var log = workFolder.resolve("build-" + minecraftVersion + ".log");
            var command = List.of(javaExecutable(), "-Xmx1G", "-jar", buildTools.getFileName().toString(),
                    "--rev", minecraftVersion, "--compile", "SPIGOT", "--nogui",
                    "--output-dir", jarsFolder.toAbsolutePath().toString(),
                    "--final-name", jar.getFileName().toString());
            Console.info("Building Spigot %s with BuildTools, this takes a few minutes. Log: %s", minecraftVersion, log);
            var process = new ProcessBuilder(command)
                    .directory(workFolder.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            if (!process.waitFor(BUILD_TIMEOUT.toMinutes(), TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("BuildTools did not finish within " + BUILD_TIMEOUT.toMinutes() + " minutes, see " + log);
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(jar)) {
                throw new IOException("BuildTools failed for Spigot " + minecraftVersion + ": " + lastLines(log, 5)
                        + " (full log: " + log + ")");
            }
            Console.info("Built Spigot %s", minecraftVersion);
            return jar;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while building Spigot " + minecraftVersion, e);
        } finally {
            BUILD_LOCK.unlock();
        }
    }

    private static void checkGit() throws IOException {
        try {
            var git = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            git.getInputStream().readAllBytes();
            if (git.waitFor(30, TimeUnit.SECONDS) && git.exitValue() == 0) {
                return;
            }
        } catch (IOException e) {
            // handled below
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new IOException("BuildTools needs git to build Spigot; install git (e.g. apt install git)");
    }

    /**
     * The Java the cloud runs on, which BuildTools also checks the version against.
     */
    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }

    private static String lastLines(Path file, int count) {
        try {
            // ISO-8859-1 accepts any byte, the log may not be valid UTF-8
            var lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.ISO_8859_1));
            lines.removeIf(String::isBlank);
            return String.join(" | ", lines.subList(Math.max(0, lines.size() - count), lines.size()));
        } catch (IOException e) {
            return "no log";
        }
    }

}
