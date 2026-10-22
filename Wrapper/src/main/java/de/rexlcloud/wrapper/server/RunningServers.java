package de.rexlcloud.wrapper.server;

import com.google.gson.reflect.TypeToken;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.server.ProxyForwarding;
import de.rexlcloud.core.server.ServerGroup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The servers that run in tmux sessions, saved so a restarted wrapper can attach to them again.
 */
public final class RunningServers {

    /**
     * What is needed to continue with a running server.
     */
    public record Entry(String name, UUID key, ServerGroup group, int port, boolean maintenance, ProxyForwarding forwarding,
                        String folder, String templateVariant, String connectToken, long startedAt, String session) {
    }

    private final Path file;

    public RunningServers(Path file) {
        this.file = file;
    }

    public synchronized List<Entry> load() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<Entry> entries = JsonConfiguration.GSON().fromJson(Files.readString(file),
                    new TypeToken<ArrayList<Entry>>() {
                    }.getType());
            return entries == null ? List.of() : entries;
        } catch (IOException | RuntimeException e) {
            Console.warn("%s could not be read, running servers are not attached again: %s", file, e.getMessage());
            return List.of();
        }
    }

    public synchronized void add(Entry entry) {
        var entries = new ArrayList<>(load());
        entries.removeIf(existing -> existing.name().equals(entry.name()));
        entries.add(entry);
        save(entries);
    }

    public synchronized void remove(String name, UUID key) {
        var entries = new ArrayList<>(load());
        if (entries.removeIf(entry -> entry.name().equals(name) && entry.key().equals(key))) {
            save(entries);
        }
    }

    /**
     * Keeps only the entries whose server is still running.
     */
    public synchronized void retain(List<Entry> running) {
        save(running);
    }

    private void save(List<Entry> entries) {
        try {
            Files.createDirectories(file.getParent());
            var temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, JsonConfiguration.GSON().toJson(entries));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Console.warn("%s could not be saved: %s", file, e.getMessage());
        }
    }

}
