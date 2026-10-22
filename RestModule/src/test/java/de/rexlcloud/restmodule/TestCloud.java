package de.rexlcloud.restmodule;

import de.rexlcloud.core.api.events.EventManager;
import de.rexlcloud.core.api.module.CloudModule;
import de.rexlcloud.core.api.module.ModuleApi;
import de.rexlcloud.core.api.module.ModuleInfo;
import de.rexlcloud.core.command.CommandManager;
import de.rexlcloud.core.command.CommandRegistry;
import de.rexlcloud.core.server.files.ServerJarManager;
import de.rexlcloud.restmodule.user.Permission;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enables the RestModule the way the master would, but against a {@link FakeModuleApi}.
 */
public final class TestCloud {

    public static final String METRICS_TOKEN = "test-metrics-token";

    private TestCloud() {
    }

    public static RestModule start(Path dataFolder, String host, int port, FakeModuleApi api) throws Exception {
        Files.createDirectories(dataFolder);
        Files.writeString(dataFolder.resolve("config.json"),
                "{\"host\":\"" + host + "\",\"port\":" + port + ",\"metricsToken\":\"" + METRICS_TOKEN + "\"}");
        var module = new RestModule();
        var enable = CloudModule.class.getDeclaredMethod("enable", ModuleInfo.class, File.class, EventManager.class,
                CommandManager.class, ServerJarManager.class, ModuleApi.class);
        enable.setAccessible(true);
        enable.invoke(module, new ModuleInfo("RestModule", RestModule.class.getName(), "dev", "rexlNico"),
                dataFolder.toFile(), new EventManager(), new CommandRegistry().getCommandManager(), null, api);
        return module;
    }

    public static void createUser(String username, String password, Permission... permissions) {
        var user = RestModule.getInstance().getUserManager().createUser(username, password).orElseThrow();
        user.addPermissions(permissions);
    }

    public static int freePort() throws IOException {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

}
