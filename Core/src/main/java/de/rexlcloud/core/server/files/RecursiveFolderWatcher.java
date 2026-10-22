package de.rexlcloud.core.server.files;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

public abstract class RecursiveFolderWatcher {

    private final WatchService watchService;
    private final Map<WatchKey, Path> keys;

    public RecursiveFolderWatcher(Path root) throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>();
        registerAll(root);
    }

    private void registerAll(final Path start) throws IOException {
        // Register the root folder and all subfolders
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    public void processEvents() {
        while (true) {
            WatchKey key;
            try {
                key = watchService.take(); // Wait for a key to be signaled
            } catch (InterruptedException ex) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                // Context is the relative path to the directory
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path name = ev.context();
                Path child = dir.resolve(name);

                onChange(child, kind);

                // If a new directory is created, register it and its subfolders
                if (kind == ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(child)) {
                            registerAll(child);
                        }
                    } catch (IOException x) {
                        // ignore to keep sample readable
                    }
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
                if (keys.isEmpty()) break; // All directories are inaccessible
            }
        }
    }

    public abstract void onChange(Path path, WatchEvent.Kind<?> kind);

}