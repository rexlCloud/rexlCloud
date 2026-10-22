package de.rexlcloud.restmodule;

import de.rexlcloud.core.api.module.CloudModule;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.restmodule.auth.SessionManager;
import de.rexlcloud.restmodule.commands.UserCommands;
import de.rexlcloud.restmodule.config.RestConfig;
import de.rexlcloud.restmodule.user.Permission;
import de.rexlcloud.restmodule.user.UserManager;
import de.rexlcloud.restmodule.web.WebServer;
import lombok.Getter;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class RestModule extends CloudModule {

    /**
     * Permissions of rexlCloud 1.x, used when the config does not list the known permissions yet.
     */
    private static final List<Permission> LEGACY_PERMISSIONS = List.of(
            Permission.CREATE_USER, Permission.DELETE_USER, Permission.CHANGE_USER_PERMISSIONS,
            Permission.SEE_ALL_SERVERS, Permission.SEE_ALL_USERS, Permission.SEE_ALL_GROUPS,
            Permission.START_SERVER, Permission.STOP_SERVER, Permission.EXECUTE_ON_SERVER,
            Permission.SEE_SERVER_CONSOLE);

    @Getter
    private static RestModule instance;
    @Getter
    private UserManager userManager;
    private SessionManager sessionManager;
    private WebServer webServer;

    @Override
    public void onEnable() {
        instance = this;
        getDataFolder().mkdirs();
        // slf4j-simple is bundled for Jetty; only show its warnings and errors
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel",
                System.getProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn"));

        var configPath = getDataFolder().toPath().resolve("config.json");
        var restConfig = JsonConfiguration.loadOrCreateConfiguration(configPath, new RestConfig());
        userManager = new UserManager();
        userManager.createAdminAccount();
        grantNewPermissions(restConfig);
        JsonConfiguration.saveConfiguration(configPath, restConfig);

        sessionManager = new SessionManager(userManager,
                Duration.ofMinutes(Math.max(1, restConfig.getSessionTimeoutMinutes())),
                Duration.ofSeconds(Math.max(5, restConfig.getConsoleTicketSeconds())));
        webServer = new WebServer(restConfig, getDataFolder().toPath(), userManager, sessionManager);
        webServer.start();
        Console.info("Enabled RestModule");

        getCommandManager().registerCommands(this, new UserCommands());
    }

    private void grantNewPermissions(RestConfig restConfig) {
        var known = EnumSet.noneOf(Permission.class);
        if (restConfig.getKnownPermissions() == null) {
            known.addAll(LEGACY_PERMISSIONS);
        } else {
            restConfig.getKnownPermissions().forEach(name -> Permission.fromName(name).ifPresent(known::add));
        }
        var newPermissions = EnumSet.complementOf(known);
        userManager.grantNewPermissions(newPermissions);
        restConfig.setKnownPermissions(Arrays.stream(Permission.values()).map(Enum::name).toList());
    }

    @Override
    public void onDisable() {
        webServer.stop();
        sessionManager.shutdown();
        getCommandManager().unregisterAllCommands(this);
    }

    @Override
    public void onReload() {
    }
}
