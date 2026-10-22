package de.rexlcloud.core.database;

import de.rexlcloud.core.config.JsonConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseCredentialsTest {

    @TempDir
    Path folder;

    @Test
    void configurationsFromBeforeTheTypeStayOnMysql() throws Exception {
        var file = folder.resolve("database.json");
        Files.writeString(file, "{\"host\":\"db.example\",\"port\":3307,\"user\":\"u\",\"password\":\"p\",\"database\":\"cloud\"}");

        var credentials = JsonConfiguration.loadOrCreateConfiguration(file, DatabaseCredentials.embedded());

        assertFalse(credentials.isEmbedded());
        assertEquals("jdbc:mysql://db.example:3307/cloud", credentials.jdbcUrl());
    }

    @Test
    void newInstallsGetTheEmbeddedDatabase() {
        var file = folder.resolve("database.json");

        var credentials = JsonConfiguration.loadOrCreateConfiguration(file, DatabaseCredentials.embedded());

        assertTrue(credentials.isEmbedded());
        assertTrue(credentials.jdbcUrl().startsWith("jdbc:h2:file:"), credentials.jdbcUrl());
        assertFalse(credentials.toString().contains("password='"), "no password in logs");
    }

    @Test
    void theEmbeddedDatabaseKeepsItsDataInFiles() throws Exception {
        // An absolute name puts the files into the test folder instead of database/
        var credentials = new DatabaseCredentials(DatabaseCredentials.H2, "", 0, "", "",
                folder.resolve("cloud").toString());
        var connection = new DatabaseConnection(credentials);
        connection.open();
        try (var sql = connection.getDataSource().getConnection(); var statement = sql.createStatement()) {
            statement.execute("CREATE TABLE players (name VARCHAR(16))");
            statement.execute("INSERT INTO players VALUES ('Notch')");
        }
        connection.close();

        var again = new DatabaseConnection(credentials);
        again.open();
        try (var sql = again.getDataSource().getConnection(); var statement = sql.createStatement();
             var result = statement.executeQuery("SELECT name FROM players")) {
            assertTrue(result.next());
            assertEquals("Notch", result.getString(1));
        }
        again.close();
        assertTrue(Files.exists(folder.resolve("cloud.mv.db")));
    }

    @Test
    void mariaDbUsesTheMysqlProtocolAndKeepsItsName() {
        var credentials = new DatabaseCredentials(DatabaseCredentials.MARIADB, "db", 3307, "cloud", "secret", "rexl");

        assertFalse(credentials.isEmbedded());
        assertEquals("jdbc:mysql://db:3307/rexl", credentials.jdbcUrl());
        assertTrue(credentials.toString().contains("type='mariadb'"));
        assertFalse(credentials.toString().contains("secret"));
    }

}
