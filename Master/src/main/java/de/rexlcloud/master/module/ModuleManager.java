package de.rexlcloud.master.module;

import de.rexlcloud.core.api.events.EventManager;
import de.rexlcloud.core.api.module.CloudModule;
import de.rexlcloud.core.api.module.ModuleApi;
import de.rexlcloud.core.api.module.ModuleClassLoader;
import de.rexlcloud.core.api.module.ModuleInfo;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.master.RexlCloudMaster;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ModuleManager {

    private static final File moduleFolder = new File("modules/");
    @Getter
    private static ModuleManager instance;
    private final ArrayList<ModuleClassLoader> moduleClassLoaders;
    @Getter
    private final EventManager eventManager;
    private final ModuleApi moduleApi;

    public ModuleManager(EventManager eventManager, ModuleApi moduleApi) {
        if (instance != null) {
            this.moduleClassLoaders = null;
            this.eventManager = null;
            this.moduleApi = null;
            return;
        }
        instance = this;
        this.moduleApi = moduleApi;
        this.moduleClassLoaders = new ArrayList<>();
        this.eventManager = eventManager;
        moduleFolder.mkdirs();
        new ModuleUpdater(moduleFolder.toPath()).update(RexlCloudMaster.getInstance().getVersion());
        var modules = moduleFolder.listFiles();
        if (modules != null) {
            if (Arrays.stream(modules).filter(File::isFile).anyMatch(file -> file.getName().endsWith(".jar"))) {
                Console.info("Loading Modules");
            } else {
                Console.info("No Modules found");
                return;
            }
            Arrays.stream(modules)
                    .filter(File::isFile)
                    .filter(file -> file.getName().endsWith(".jar"))
                    .forEach(file -> {
                        try {
                            var moduleClassLoader = new ModuleClassLoader(file, getClass().getClassLoader());
                            if (moduleClassLoader.getCloudModule() == null) {
                                return;
                            }
                            moduleClassLoaders.add(moduleClassLoader);
                            Console.info("Initialized module %s", moduleClassLoader.getModuleInfo().getName());
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        var commandManager = RexlCloudMaster.getInstance().getCommandRegistry().getCommandManager();
        var serverJarManager = RexlCloudMaster.getInstance().getServerJarManager();
        moduleClassLoaders.forEach(moduleClassLoader ->
                moduleClassLoader.enable(
                        moduleClassLoader.getModuleInfo(),
                        new File(moduleFolder, moduleClassLoader.getModuleInfo().getName()),
                        eventManager,
                        commandManager,
                        serverJarManager,
                        moduleApi
                ));
    }

    public List<String> getModuleFileNames() {
        var array = moduleFolder.listFiles();
        if (array == null) {
            return new ArrayList<>();
        }
        return Arrays.stream(array).filter(File::isFile).map(File::getName).filter(name -> name.endsWith(".jar")).toList();
    }

    public List<CloudModule> getModules() {
        return moduleClassLoaders.stream().map(ModuleClassLoader::getCloudModule).toList();
    }

    public List<String> getModuleNames() {
        return moduleClassLoaders.stream().map(ModuleClassLoader::getModuleInfo).map(ModuleInfo::getName).toList();
    }

    public Optional<CloudModule> getModuleByName(String name) {
        return moduleClassLoaders.stream()
                .filter(moduleClassLoader -> moduleClassLoader.getModuleInfo().getName().equals(name))
                .map(ModuleClassLoader::getCloudModule)
                .findAny();
    }

    public void disableModule(CloudModule module) {
        new ArrayList<>(moduleClassLoaders).stream()
                .filter(moduleClassLoader -> moduleClassLoader.getCloudModule().equals(module))
                .findAny().ifPresent(moduleClassLoader -> {
                    moduleClassLoader.getCloudModule().onDisable();
                    moduleClassLoaders.remove(moduleClassLoader);
                    module.getCommandManager().unregisterAllCommands(module);
                    try {
                        moduleClassLoader.close();
                    } catch (IOException ignored) {
                    }
                });
    }

    public void reloadModule(CloudModule module) {
        new ArrayList<>(moduleClassLoaders).stream()
                .filter(moduleClassLoader -> moduleClassLoader.getCloudModule().equals(module))
                .findAny().ifPresent(moduleClassLoader -> moduleClassLoader.getCloudModule().onReload());
    }

    public void reloadAllModules() {
        new ArrayList<>(moduleClassLoaders).stream()
                .map(ModuleClassLoader::getCloudModule)
                .forEach(CloudModule::onReload);
    }

    public ModuleLoadResult loadModule(String moduleJarName) {
        return loadModule(new File(moduleFolder, moduleJarName));
    }

    public ModuleLoadResult loadModule(File jarFile) {
        if (jarFile.isFile() && jarFile.getName().endsWith(".jar")) {
            try {
                var moduleClassLoader = new ModuleClassLoader(jarFile, getClass().getClassLoader());
                if (moduleClassLoader.getCloudModule() == null) {
                    return new ModuleLoadResult(ModuleLoadResultType.LOAD_FAILED, moduleClassLoader.getModuleInfo().getName());
                }
                if (getModuleByName(moduleClassLoader.getModuleInfo().getName()).isPresent()) {
                    return new ModuleLoadResult(ModuleLoadResultType.ALREADY_LOADED, moduleClassLoader.getModuleInfo().getName());
                }
                moduleClassLoaders.add(moduleClassLoader);
                Console.info("Initialized module %s", moduleClassLoader.getModuleInfo().getName());
                var commandManager = RexlCloudMaster.getInstance().getCommandRegistry().getCommandManager();
                var serverJarManager = RexlCloudMaster.getInstance().getServerJarManager();
                moduleClassLoader.enable(
                        moduleClassLoader.getModuleInfo(),
                        new File(moduleFolder, moduleClassLoader.getModuleInfo().getName()),
                        eventManager,
                        commandManager,
                        serverJarManager,
                        moduleApi
                );
                return new ModuleLoadResult(ModuleLoadResultType.SUCCESS, moduleClassLoader.getModuleInfo().getName());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        return new ModuleLoadResult(ModuleLoadResultType.NOT_A_FILE, jarFile.getName());
    }

    public void stopModules() {
        moduleClassLoaders.stream().map(ModuleClassLoader::getCloudModule).forEach(module -> {
            module.onDisable();
            module.getCommandManager().unregisterAllCommands(module);
        });
        moduleClassLoaders.forEach(moduleClassLoader -> {
            try {
                moduleClassLoader.close();
            } catch (IOException ignored) {
            }
        });
        moduleClassLoaders.clear();
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ModuleLoadResult {
        private ModuleLoadResultType type;
        private String moduleName;
    }

    public enum ModuleLoadResultType {
        SUCCESS,
        NOT_A_FILE,
        LOAD_FAILED,
        ALREADY_LOADED
    }

}
