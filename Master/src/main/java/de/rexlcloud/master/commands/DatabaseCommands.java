package de.rexlcloud.master.commands;

import de.rexlcloud.core.command.CommandDescription;
import de.rexlcloud.core.command.CommandMethod;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.database.DatabaseConnection;
import de.rexlcloud.core.database.DatabaseCredentials;
import de.rexlcloud.core.database.DatabaseTools;
import de.rexlcloud.master.RexlCloudMaster;

import java.io.IOException;
import java.nio.file.Files;

public class DatabaseCommands {

    @CommandMethod("database backup")
    @CommandDescription("Back up the embedded database into database/backups/")
    public void backup() {
        var master = RexlCloudMaster.getInstance();
        if (!master.getDatabaseCredentials().isEmbedded()) {
            Console.error("The cloud uses MySQL; back it up with mysqldump or your database host's tools");
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                var file = master.backupDatabase();
                Console.info("Backed up the database to %s", file);
            } catch (IOException e) {
                Console.error("%s", e.getMessage());
            }
        });
    }

    @CommandMethod("database copy")
    @CommandDescription("Copy all data into the database in config/database-target.json, e.g. from MySQL to H2")
    public void copy() {
        var master = RexlCloudMaster.getInstance();
        var targetFile = master.getConfigPath().resolve("database-target.json");
        if (!Files.isRegularFile(targetFile)) {
            Console.error("Create %s first, in the format of database.json, with the database to copy into", targetFile);
            return;
        }
        var credentials = JsonConfiguration.loadConfiguration(targetFile, DatabaseCredentials.class);
        if (credentials == null) {
            Console.error("%s could not be read", targetFile);
            return;
        }
        Console.info("Copying the data into %s...", credentials);
        Thread.ofVirtual().start(() -> {
            var target = new DatabaseConnection(credentials);
            try {
                target.open();
                if (target.getDataSource() == null) {
                    Console.error("Could not connect to the target database");
                    return;
                }
                var counts = DatabaseTools.copy(master.getDatabaseConnection(), target, RexlCloudMaster.DATABASE_TABLES);
                counts.forEach((table, rows) -> Console.info("%s: %s row(s)", table.getSimpleName(), rows));
                Console.info("Done. To use it: stop the master, rename database-target.json to database.json and start it");
            } catch (RuntimeException e) {
                Console.error("The copy failed: %s", e.getMessage());
            } finally {
                target.close();
            }
        });
    }

}
