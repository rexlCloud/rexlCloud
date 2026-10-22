package de.rexlcloud.core.server.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Names, paths and contents of templates as they are synced from the master to the wrappers.
 * <p>
 * A template is either a group ({@code Lobby}: the folder {@code templates/Lobby} with {@code default/} and other
 * variants) or a global template ({@code global/server}, {@code global/proxy}) that is copied into every server or
 * proxy. File paths inside a template are relative and use {@code /}.
 */
public final class Templates {

    public static final String GLOBAL_SERVER = "global/server";
    public static final String GLOBAL_PROXY = "global/proxy";
    public static final Set<String> GLOBAL = Set.of(GLOBAL_SERVER, GLOBAL_PROXY);
    /**
     * No group may have this name: its folder holds the global templates.
     */
    public static final String RESERVED_GROUP_NAME = "global";
    /**
     * Files whose name starts with this are temporary (e.g. downloads in progress) and never synced.
     */
    public static final String TEMPORARY_PREFIX = ".rexlcloud-";

    private static final Pattern GROUP = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Map<Path, Cached> HASHES = new ConcurrentHashMap<>();

    private record Cached(long size, long modified, String sha256) {
    }

    private Templates() {
    }

    public static boolean isValidName(String template) {
        return template != null && (GLOBAL.contains(template)
                || (GROUP.matcher(template).matches() && !template.equalsIgnoreCase(RESERVED_GROUP_NAME)));
    }

    /**
     * Folder of a template below the templates root.
     *
     * @throws IllegalArgumentException for names that are no template
     */
    public static Path folder(Path root, String template) {
        if (!isValidName(template)) {
            throw new IllegalArgumentException("Invalid template name " + template);
        }
        return root.resolve(template);
    }

    /**
     * A file of a template, refusing paths that would leave the template folder ({@code ../}, absolute paths).
     *
     * @throws IllegalArgumentException for invalid names or paths
     */
    public static Path file(Path root, String template, String relativePath) {
        var folder = folder(root, template).toAbsolutePath().normalize();
        if (relativePath == null || relativePath.isBlank() || relativePath.contains("\\")
                || relativePath.indexOf('\0') >= 0 || relativePath.startsWith("/") || relativePath.contains(":")) {
            throw new IllegalArgumentException("Invalid template file path " + relativePath);
        }
        var file = folder.resolve(relativePath).normalize();
        if (!file.startsWith(folder) || file.equals(folder)) {
            throw new IllegalArgumentException("Template file path " + relativePath + " leaves its template");
        }
        return file;
    }

    /**
     * Files of a template with size and SHA-256, keyed by relative path. Hashes are cached while a file keeps its
     * size and modification time. A missing folder has no files.
     */
    public static Map<String, TemplateFile> manifest(Path folder) throws IOException {
        var files = new TreeMap<String, TemplateFile>();
        if (!Files.isDirectory(folder)) {
            return files;
        }
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isRegularFile() && !file.getFileName().toString().startsWith(TEMPORARY_PREFIX)) {
                    var relative = folder.relativize(file).toString().replace('\\', '/');
                    files.put(relative, new TemplateFile(attributes.size(), sha256(file, attributes)));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                // e.g. deleted while scanning; the next sync picks it up
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static String sha256(Path file, BasicFileAttributes attributes) throws IOException {
        var key = file.toAbsolutePath().normalize();
        var modified = attributes.lastModifiedTime().toMillis();
        var cached = HASHES.get(key);
        if (cached != null && cached.size() == attributes.size() && cached.modified() == modified) {
            return cached.sha256();
        }
        String hash;
        try (var in = Files.newInputStream(file)) {
            hash = sha256(in);
        }
        HASHES.put(key, new Cached(attributes.size(), modified, hash));
        return hash;
    }

    public static String sha256(InputStream in) throws IOException {
        var digest = newDigest();
        var buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
