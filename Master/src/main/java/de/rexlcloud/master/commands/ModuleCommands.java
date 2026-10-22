package de.rexlcloud.master.commands;

import de.rexlcloud.core.api.module.CloudModule;
import de.rexlcloud.core.command.Argument;
import de.rexlcloud.core.command.CommandDescription;
import de.rexlcloud.core.command.CommandMethod;
import de.rexlcloud.core.command.ObjectParser;
import de.rexlcloud.core.command.Suggestion;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.module.ModuleManager;
import de.rexlcloud.master.module.ModuleUpdater;
import de.rexlcloud.master.module.OfficialModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ModuleCommands {

    @CommandMethod("modules disable <module>")
    public void stopModule(@Argument(value = "module", suggestions = "moduleNames") CloudModule module) {
        if (module == null) {
            Console.error("This module is not loaded");
            return;
        }
        ModuleManager.getInstance().disableModule(module);
        Console.info("Successfully disabled the module %s", module.getModuleInfo().getName());
    }

    @CommandMethod("modules load <module>")
    public void loadModule(@Argument(value = "module", suggestions = "moduleFiles") String file) {
        var result = ModuleManager.getInstance().loadModule(file);
        switch (result.getType()) {
            case SUCCESS -> Console.info("Successfully loaded %s", result.getModuleName());
            case LOAD_FAILED -> Console.error("Error while loading %s!", result.getModuleName());
            case NOT_A_FILE ->
                    Console.error("Error while loading %s is not in correct format!",  result.getModuleName());
            case ALREADY_LOADED -> Console.error("The module %s is already loaded!", result.getModuleName());
        }
    }

    @CommandMethod("modules reload <module>")
    public void reloadModule(@Argument(value = "module", suggestions = "moduleNames") CloudModule module) {
        if (module == null) {
            Console.error("This module is not loaded");
            return;
        }
        ModuleManager.getInstance().reloadModule(module);
        Console.info("Successfully reloaded the module %s", module.getModuleInfo().getName());
    }

    @CommandMethod("modules reloadAll")
    public void reloadAllModule() {
        ModuleManager.getInstance().reloadAllModules();
        Console.info("Successfully reloaded %s modules", ModuleManager.getInstance().getModules().size());
    }

    @CommandMethod("modules list")
    public void listModules() {
        ModuleManager.getInstance().getModules().forEach(cloudModule -> {
            Console.info("%s %s from %s",
                    cloudModule.getModuleInfo().getName(),
                    cloudModule.getModuleInfo().getVersion(),
                    cloudModule.getModuleInfo().getAuthor()
            );
        });
    }

    @CommandMethod("modules available")
    @CommandDescription("Official modules that ship with the cloud and whether they are installed")
    public void availableModules() {
        var updater = new ModuleUpdater(Path.of("modules"));
        for (var module : OfficialModule.ALL) {
            Console.info("%s (%s): %s", module.name(), updater.isInstalled(module) ? "installed" : "not installed",
                    module.description());
        }
    }

    @CommandMethod("modules install <module>")
    @CommandDescription("Downloads an official module in the master's version and loads it")
    public void installModule(@Argument(value = "module", suggestions = "officialModules") String name) {
        var module = OfficialModule.byName(name);
        if (module.isEmpty()) {
            Console.error("%s is not an official module; see \"modules available\"", name);
            return;
        }
        var updater = new ModuleUpdater(Path.of("modules"));
        if (updater.isInstalled(module.get())) {
            Console.error("The %s is already installed", module.get().name());
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                var jar = updater.install(module.get(), RexlCloudMaster.getInstance().getVersion());
                Console.info("Downloaded the %s", module.get().name());
                if (ModuleManager.getInstance() == null) {
                    // The master is still starting; it loads every jar in modules/ when it gets there
                    Console.info("It is loaded as soon as the master has started");
                } else {
                    loadModule(jar.getFileName().toString());
                }
            } catch (IOException e) {
                Console.error("Could not install the %s: %s", module.get().name(), ModuleUpdater.describe(e));
            }
        });
    }

    @Suggestion("officialModules")
    public List<String> getOfficialModuleNames() {
        return OfficialModule.ALL.stream().map(OfficialModule::name).toList();
    }

    @ObjectParser(CloudModule.class)
    public CloudModule parsModule(String input) {
        return ModuleManager.getInstance().getModuleByName(input).orElse(null);
    }

    @Suggestion("moduleFiles")
    public List<String> getModuleFileNames() {
        return ModuleManager.getInstance().getModuleFileNames();
    }

    @Suggestion("moduleNames")
    public List<String> getModuleNames() {
        return ModuleManager.getInstance().getModuleNames();
    }

}
