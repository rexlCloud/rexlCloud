package de.rexlcloud.master.commands;

import de.rexlcloud.core.command.Argument;
import de.rexlcloud.core.command.CommandMethod;
import de.rexlcloud.core.command.Suggestion;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.wrapper.WrapperCredentials;

import java.util.List;
import java.util.UUID;

public class WrapperCommands {

    @CommandMethod("wrapper add <address> <key>")
    public void addWrapper(@Argument("address") String address, @Argument("key") UUID key) {
        var wrapperManager = RexlCloudMaster.getInstance().getWrapperManager();
        var wrapperName = wrapperManager.getNewWrapperName();
        if (wrapperManager.addWrapperCredentials(new WrapperCredentials(wrapperName, address, key))) {
            Console.info("Wrapper %s added", wrapperName);
            Console.info("Optional, against attacks on the first connection: set \"masterFingerprint\": \"%s\" "
                    + "in config/wrapper.json on the wrapper", RexlCloudMaster.getInstance().getTls().getFingerprint());
        }
    }

    @CommandMethod("wrapper stop <name>")
    public void stopWrapper(@Argument(value = "name", suggestions = "wrapperNames") String wrapperName) {
        var wrapperManager = RexlCloudMaster.getInstance().getWrapperManager();
        var wrapper = wrapperManager.getWrapperByName(wrapperName);
        if (wrapper == null) {
            Console.warn("The wrapper %s cannot be stopped", wrapperName);
            return;
        }
        wrapperManager.stopWrapper(wrapper);
    }

    @Suggestion("wrapperNames")
    public List<String> getWrapperNames() {
        return RexlCloudMaster.getInstance().getWrapperManager().getWrappers().keySet().stream().toList();
    }

}
