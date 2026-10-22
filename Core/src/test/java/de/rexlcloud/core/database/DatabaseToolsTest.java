package de.rexlcloud.core.database;

import de.rexlcloud.core.database.anotations.DatabaseData;
import de.rexlcloud.core.database.anotations.DatabaseValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseToolsTest {

    @TempDir
    Path folder;

    @DatabaseData(tableName = "tools_players")
    public static class PlayerRow {
        @DatabaseValue(id = true)
        int id;
        @DatabaseValue
        String name;

        public PlayerRow() {
        }

        PlayerRow(String name) {
            this.name = name;
        }
    }

    private DatabaseConnection embedded(String name) {
        var connection = new DatabaseConnection(new DatabaseCredentials(DatabaseCredentials.H2, "", 0, "", "",
                folder.resolve(name).toString()));
        connection.open();
        return connection;
    }

    @Test
    void copiesAllRowsIntoAnEmptyDatabase() {
        var from = embedded("from");
        var to = embedded("to");
        try {
            from.getObjectHandler().createTable(PlayerRow.class).join();
            for (var name : List.of("Notch", "jeb_", "Dinnerbone")) {
                from.getObjectHandler().insertObject(new PlayerRow(name)).join();
            }

            var counts = DatabaseTools.copy(from, to, List.of(PlayerRow.class));

            assertEquals(3, counts.get(PlayerRow.class));
            var copied = to.getObjectHandler().getAllObjects(PlayerRow.class).join().stream().map(row -> row.name).sorted().toList();
            assertEquals(List.of("Dinnerbone", "Notch", "jeb_"), copied);

            var e = assertThrows(IllegalStateException.class, () -> DatabaseTools.copy(from, to, List.of(PlayerRow.class)));
            assertTrue(e.getMessage().contains("empty"), "never copied twice");
        } finally {
            from.close();
            to.close();
        }
    }

    @Test
    void backupsOfTheEmbeddedDatabaseKeepTheNewest() throws Exception {
        var database = embedded("cloud");
        try {
            database.getObjectHandler().createTable(PlayerRow.class).join();
            var backups = folder.resolve("backups");
            for (var i = 0; i < 3; i++) {
                assertTrue(Files.size(DatabaseTools.backup(database, backups, 2)) > 0);
                // The backup names have a resolution of one second
                Thread.sleep(1100);
            }
            try (var files = Files.list(backups)) {
                assertEquals(2, files.count());
            }
        } finally {
            database.close();
        }
    }

}
