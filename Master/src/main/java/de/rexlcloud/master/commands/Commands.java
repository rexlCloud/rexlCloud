package de.rexlcloud.master.commands;

import de.rexlcloud.core.command.Argument;
import de.rexlcloud.core.command.CommandMethod;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.master.RexlCloudMaster;


public class Commands {

    @CommandMethod("stop")
    public void stopCommand() {
        // The shutdown hook disconnects the wrappers, which then stop their servers
        System.exit(0);
    }

    @CommandMethod("clear")
    public void clearCommand() {
        Console.clear();
    }

    @CommandMethod("maintenance <active>")
    public void maintenanceCommand(@Argument(value = "active", suggestions = "boolean") boolean active) {
        RexlCloudMaster.getInstance().setMaintenance(active);
        if (active) {
            Console.write("Maintenance mode enabled!");
        } else {
            Console.write("Maintenance mode disabled!");
        }
    }

}
