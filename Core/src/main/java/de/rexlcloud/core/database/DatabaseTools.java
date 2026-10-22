package de.rexlcloud.core.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backups of the embedded database and copying the cloud's data between two databases (e.g. from MySQL to H2).
 */
public final class DatabaseTools {

    private static final DateTimeFormatter BACKUP_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private DatabaseTools() {
    }

    /**
     * Writes an online backup of an embedded H2 database (consistent while the cloud keeps writing) as a zip into
     * {@code folder} and deletes all but the newest {@code keep} backups.
     *
     * @return the new backup
     */
    public static Path backup(DatabaseConnection database, Path folder, int keep) throws IOException {
        Files.createDirectories(folder);
        var file = folder.resolve("rexlcloud-" + LocalDateTime.now().format(BACKUP_NAME) + ".zip").toAbsolutePath();
        try {
            database.execute("BACKUP TO '" + file.toString().replace("'", "''") + "'").join();
        } catch (RuntimeException e) {
            throw new IOException("The backup failed: " + rootMessage(e), e);
        }
        List<Path> backups;
        try (var files = Files.list(folder)) {
            backups = files.filter(path -> path.getFileName().toString().matches("rexlcloud-.*\\.zip"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        for (var old : backups.subList(Math.min(keep, backups.size()), backups.size())) {
            Files.deleteIfExists(old);
        }
        return file;
    }

    /**
     * Copies all rows of the tables from one database into another. The target tables are created if needed and
     * have to be empty, so nothing is mixed or written twice. Generated ids are assigned anew.
     *
     * @return the number of rows per table class
     * @throws IllegalStateException if a target table already has rows
     */
    public static Map<Class<?>, Integer> copy(DatabaseConnection from, DatabaseConnection to, List<Class<?>> tables) {
        var source = from.getObjectHandler();
        var target = to.getObjectHandler();
        for (var table : tables) {
            target.createTable(table).join();
            if (!target.getAllObjects(table).join().isEmpty()) {
                throw new IllegalStateException("The target already has data in the table of " + table.getSimpleName()
                        + "; copy only into an empty database");
            }
        }
        var counts = new LinkedHashMap<Class<?>, Integer>();
        for (var table : tables) {
            var rows = source.getAllObjects(table).join();
            for (var row : rows) {
                if (!target.insertObject(row).join()) {
                    throw new IllegalStateException("A row of " + table.getSimpleName() + " could not be written");
                }
            }
            counts.put(table, rows.size());
        }
        return counts;
    }

    private static String rootMessage(Throwable e) {
        while (e.getCause() != null) {
            e = e.getCause();
        }
        return e.getMessage();
    }

}
