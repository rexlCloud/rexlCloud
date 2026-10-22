package de.rexlcloud.wrapper.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packs the files of a running server for saving them as a template: everything that makes up the server, but not
 * what the cloud or the server software recreate on every start.
 */
public final class ServerSnapshot {

    /**
     * Top-level folders that are logs, caches or downloads of the server software.
     */
    private static final Set<String> SKIPPED_FOLDERS = Set.of("logs", "crash-reports", "cache", "libraries", "versions",
            ".paper-remapped", "debug");
    /**
     * Top-level files the cloud writes before every start, and the jar the group installs.
     */
    private static final Set<String> SKIPPED_FILES = Set.of("cloud.json", "eula.txt", "server.jar", "proxy.jar");
    /**
     * The cloud plugin is added on every start in the version of the wrapper.
     */
    private static final Pattern CLOUD_PLUGIN = Pattern.compile("plugins/(bridge|proxy)-[^/]*\\.jar");

    private ServerSnapshot() {
    }

    /**
     * Whether a file (path relative to the server folder, with '/') belongs into the template.
     */
    static boolean isSaved(String path) {
        var slash = path.indexOf('/');
        var first = slash < 0 ? path : path.substring(0, slash);
        if (slash >= 0 && SKIPPED_FOLDERS.contains(first)) {
            return false;
        }
        if (slash < 0 && (SKIPPED_FILES.contains(path) || path.startsWith(".rexlcloud-"))) {
            // ... and the files the wrapper uses to run the server (tmux start script, console, exit code)
            return false;
        }
        // Worlds are locked while the server runs
        return !path.endsWith("session.lock") && !CLOUD_PLUGIN.matcher(path).matches();
    }

    /**
     * Writes the saved files of {@code folder} as a zip.
     *
     * @return how many files were packed
     */
    public static int zip(Path folder, OutputStream out) throws IOException {
        var count = new int[1];
        try (var zip = new ZipOutputStream(out)) {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                    var relative = folder.relativize(dir).toString().replace('\\', '/');
                    return relative.isEmpty() || isSaved(relative + "/x") ? FileVisitResult.CONTINUE
                            : FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    var relative = folder.relativize(file).toString().replace('\\', '/');
                    if (attributes.isRegularFile() && isSaved(relative)) {
                        zip.putNextEntry(new ZipEntry(relative));
                        Files.copy(file, zip);
                        zip.closeEntry();
                        count[0]++;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    // e.g. a file the server deleted meanwhile
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return count[0];
    }

}
