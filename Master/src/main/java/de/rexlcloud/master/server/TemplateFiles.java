package de.rexlcloud.master.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.rexlcloud.core.server.template.Templates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;

/**
 * Files of the templates on the master, for the web panel's file manager. A template is a group with a variant
 * ({@code Lobby/default}, {@code Lobby/map-desert}) or {@code global/server} and {@code global/proxy}. Changes reach
 * the wrappers through the template sync.
 */
public class TemplateFiles {

    private final Path root;

    public TemplateFiles(Path root) {
        this.root = root;
    }

    /**
     * Thrown for templates or paths that do not exist or are not allowed.
     */
    public static class TemplateFileException extends IOException {
        private final boolean notFound;

        TemplateFileException(String message, boolean notFound) {
            super(message);
            this.notFound = notFound;
        }

        public boolean isNotFound() {
            return notFound;
        }
    }

    /**
     * The folder of a template. {@code group} has to be a non-static group (checked by the caller) or "global".
     */
    public Path templateFolder(String group, String variant) throws TemplateFileException {
        if (group.equalsIgnoreCase(Templates.RESERVED_GROUP_NAME)) {
            if (!variant.equals("server") && !variant.equals("proxy")) {
                throw new TemplateFileException("The global templates are global/server and global/proxy", true);
            }
            return root.resolve(Templates.RESERVED_GROUP_NAME).resolve(variant);
        }
        if (!TemplateUploads.VARIANT.matcher(variant).matches()) {
            throw new TemplateFileException("A variant name may only contain letters, numbers, '-' and '_'", false);
        }
        try {
            return Templates.folder(root, group).resolve(variant);
        } catch (IllegalArgumentException e) {
            throw new TemplateFileException("Unknown template " + group, true);
        }
    }

    /**
     * A path inside a template; nothing outside it and no temporary files of the cloud.
     */
    public Path resolve(String group, String variant, String path) throws TemplateFileException {
        var folder = templateFolder(group, variant).toAbsolutePath().normalize();
        var relative = path == null ? "" : path.replace('\\', '/').strip();
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        var file = folder.resolve(relative).normalize();
        if (!file.startsWith(folder)) {
            throw new TemplateFileException("The path " + path + " is outside the template", false);
        }
        for (var part : folder.relativize(file)) {
            if (part.toString().startsWith(Templates.TEMPORARY_PREFIX)) {
                throw new TemplateFileException("The path " + path + " is not allowed", false);
            }
        }
        return file;
    }

    /**
     * Variants of a group's template; "default" is always there.
     */
    public List<String> variants(String group) throws IOException {
        var folder = Templates.folder(root, group);
        var variants = new java.util.TreeSet<String>();
        variants.add("default");
        if (Files.isDirectory(folder)) {
            try (var entries = Files.list(folder)) {
                entries.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> TemplateUploads.VARIANT.matcher(name).matches())
                        .forEach(variants::add);
            }
        }
        return List.copyOf(variants);
    }

    /**
     * The entries of a folder in a template: name, directory or not, size and last change (epoch millis).
     */
    public JsonArray list(String group, String variant, String path) throws IOException {
        var folder = resolve(group, variant, path);
        if (!Files.isDirectory(folder)) {
            if (path == null || path.isBlank()) {
                // A variant that has no files yet
                return new JsonArray();
            }
            throw new TemplateFileException("The folder " + path + " does not exist", true);
        }
        var array = new JsonArray();
        try (var entries = Files.list(folder)) {
            for (var entry : entries.sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                    .thenComparing(p -> p.getFileName().toString().toLowerCase())).toList()) {
                var name = entry.getFileName().toString();
                if (name.startsWith(Templates.TEMPORARY_PREFIX)) {
                    continue;
                }
                var attributes = Files.readAttributes(entry, BasicFileAttributes.class);
                var json = new JsonObject();
                json.addProperty("name", name);
                json.addProperty("directory", attributes.isDirectory());
                json.addProperty("size", attributes.isDirectory() ? 0 : attributes.size());
                json.addProperty("modified", attributes.lastModifiedTime().toMillis());
                array.add(json);
            }
        }
        return array;
    }

    /**
     * A file to read.
     */
    public Path file(String group, String variant, String path) throws TemplateFileException {
        var file = resolve(group, variant, path);
        if (!Files.isRegularFile(file)) {
            throw new TemplateFileException("The file " + path + " does not exist", true);
        }
        return file;
    }

    /**
     * Writes a file, replacing it if it exists. It appears in one step, so the template sync never sends half a file.
     *
     * @param maxBytes larger content is refused
     * @return the size written
     */
    public long write(String group, String variant, String path, InputStream content, long maxBytes) throws IOException {
        var file = resolve(group, variant, path);
        if (file.equals(templateFolder(group, variant).toAbsolutePath().normalize())) {
            throw new TemplateFileException("A file name is needed", false);
        }
        if (Files.isDirectory(file)) {
            throw new TemplateFileException(path + " is a folder", false);
        }
        Files.createDirectories(file.getParent());
        var temporary = file.resolveSibling(Templates.TEMPORARY_PREFIX + "upload-" + file.getFileName());
        try {
            long size;
            try (var out = Files.newOutputStream(temporary); var limited = content) {
                var buffer = new byte[64 * 1024];
                size = 0;
                int read;
                while ((read = limited.read(buffer)) != -1) {
                    size += read;
                    if (size > maxBytes) {
                        throw new TemplateFileException("The file is larger than " + maxBytes / (1024 * 1024) + " MB", false);
                    }
                    out.write(buffer, 0, read);
                }
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return size;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void createFolder(String group, String variant, String path) throws IOException {
        var folder = resolve(group, variant, path);
        if (Files.isRegularFile(folder)) {
            throw new TemplateFileException(path + " is a file", false);
        }
        Files.createDirectories(folder);
    }

    /**
     * Deletes a file or a folder with everything in it. The whole default template cannot be deleted.
     */
    public void delete(String group, String variant, String path) throws IOException {
        var target = resolve(group, variant, path);
        var folder = templateFolder(group, variant).toAbsolutePath().normalize();
        if (target.equals(folder) && (variant.equals("default") || group.equalsIgnoreCase(Templates.RESERVED_GROUP_NAME))) {
            throw new TemplateFileException("This template cannot be deleted, only files in it", false);
        }
        if (!Files.exists(target)) {
            throw new TemplateFileException("The path " + path + " does not exist", true);
        }
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                try {
                    Files.delete(dir);
                } catch (DirectoryNotEmptyException | NoSuchFileException ignored) {
                    // e.g. a temporary file appeared meanwhile
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
