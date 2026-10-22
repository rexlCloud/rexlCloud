package de.rexlcloud.master;

import de.rexlcloud.master.commands.DatabaseCommands;
import de.rexlcloud.core.database.DatabaseTools;
import de.rexlcloud.core.api.PlayerDatabaseData;
import de.rexlcloud.core.api.events.EventManager;
import de.rexlcloud.core.command.CommandRegistry;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.config.MessageKey;
import de.rexlcloud.core.config.PluginMessageConfig;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.database.DatabaseConnection;
import de.rexlcloud.core.database.DatabaseCredentials;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.MaintenanceModeChangePacket;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.core.server.ServerManager;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.core.server.files.ServerJarManager;
import de.rexlcloud.master.commands.*;
import de.rexlcloud.master.config.MasterConfig;
import de.rexlcloud.master.database.CloudStats;
import de.rexlcloud.master.database.PlayerData;
import de.rexlcloud.master.module.MasterModuleApi;
import de.rexlcloud.master.module.ModuleManager;
import de.rexlcloud.master.module.ModuleUpdater;
import de.rexlcloud.master.module.OfficialModule;
import de.rexlcloud.master.setup.ConsoleSetupIo;
import de.rexlcloud.master.setup.FirstStartSetup;
import de.rexlcloud.master.setup.ServerDatabaseCheck;
import de.rexlcloud.master.network.MasterTls;
import de.rexlcloud.master.network.WrapperServer;
import de.rexlcloud.master.player.PlayerManager;
import de.rexlcloud.master.server.Server;
import de.rexlcloud.master.server.ServerGroupManager;
import de.rexlcloud.master.server.TemplateSync;
import de.rexlcloud.master.server.TemplateUploads;
import de.rexlcloud.master.wrapper.Wrapper;
import de.rexlcloud.master.wrapper.WrapperManager;
import lombok.Getter;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

@Getter
public class RexlCloudMaster {

    @Getter
    private static RexlCloudMaster instance;
    private final MasterConfig masterConfig;
    private final PluginMessageConfig pluginMessageConfig;
    private final Console console;
    private final CommandRegistry commandRegistry;
    private final WrapperManager wrapperManager;
    private final ServerGroupManager serverGroupManager;
    private MasterTls tls;
    private final TemplateSync templateSync = new TemplateSync(Path.of("templates"));
    private final TemplateUploads templateUploads = new TemplateUploads(Path.of("templates"));
    private final ServerManager<Server> serverManager;
    private final PlayerManager playerManager;
    private final ServerJarManager serverJarManager;
    private final DatabaseConnection databaseConnection;
    private final EventManager eventManager;
    private final boolean isDebug;
    private final String version;
    private final AtomicBoolean stopped = new AtomicBoolean();
    /**
     * The tables of the cloud, e.g. for copying them into another database.
     */
    public static final List<Class<?>> DATABASE_TABLES = List.of(PlayerData.class, CloudStats.class, PlayerDatabaseData.class);
    @Getter
    private DatabaseCredentials databaseCredentials;
    private final long startedAt = System.currentTimeMillis();
    /**
     * After a start, the master waits this long at most for its wrappers before it starts new servers.
     */
    private static final long WRAPPER_WAIT_MILLIS = 60_000;
    @Getter
    private final Path configPath = Path.of("config");
    private static final Path MODULES = Path.of("modules");

    public RexlCloudMaster(String[] args) throws IOException {
        // Libraries (HikariCP, Netty, Javalin) log through SLF4J: only their warnings and errors. Set here because the
        // launcher's class loader keeps simplelogger.properties out of reach.
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel",
                System.getProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn"));
        var arguments = Arrays.asList(args);
        version = arguments.stream().filter(s -> s.startsWith("--version=")).map(s -> s.split("=")[1]).findAny().orElse("UNKNOWN");
        instance = this;
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        Files.createDirectories(configPath);
        // No master.json yet: a new install. --no-setup skips the questions (e.g. configs written by a script).
        var firstStart = !Files.exists(configPath.resolve("master.json")) && !arguments.contains("--no-setup");
        var databaseConfigured = Files.exists(configPath.resolve("database.json"));

        commandRegistry = new CommandRegistry();
        commandRegistry.addCommand(new WrapperCommands());
        commandRegistry.addCommand(new GroupCommand());
        commandRegistry.addCommand(new ServerCommands());
        commandRegistry.addCommand(new Commands());
        commandRegistry.addCommand(new ModuleCommands());
        commandRegistry.addCommand(new DatabaseCommands());
        // Before the database: the first-start setup asks for it on this console
        console = new Console(commandRegistry);
        if (firstStart) {
            runFirstStartSetup(!databaseConfigured);
        }

        masterConfig = JsonConfiguration.loadOrCreateConfiguration(configPath.resolve("master.json"),
                new MasterConfig());
        if (masterConfig.completeDefaults()) {
            JsonConfiguration.saveConfiguration(configPath.resolve("master.json"), masterConfig);
        }
        pluginMessageConfig = JsonConfiguration.loadOrCreateConfiguration(configPath.resolve("messages.json"),
                new PluginMessageConfig());
        if (pluginMessageConfig.messages().size() != MessageKey.values().length) {
            pluginMessageConfig.loadDefault();
            JsonConfiguration.saveConfiguration(configPath.resolve("messages.json"), pluginMessageConfig);
        }

        isDebug = masterConfig.debug() || arguments.contains("--debug");

        databaseCredentials = JsonConfiguration
                .loadOrCreateConfiguration(configPath.resolve("database.json"), DatabaseCredentials.embedded());
        databaseConnection = new DatabaseConnection(databaseCredentials);
        databaseConnection.open();
        // Create all database table
        for (var table : DATABASE_TABLES) {
            databaseConnection.getObjectHandler().createTable(table).exceptionally(e -> {
                Console.error("Could not create the database table for %s: %s", table.getSimpleName(), rootMessage(e));
                return false;
            });
        }

        wrapperManager = new WrapperManager();
        serverManager = new ServerManager<>(true);

        if (isDebug) {
            console.setDebug();
        }
        new Thread(console).start();

        serverGroupManager = new ServerGroupManager();
        // Servers of deleted groups are not started again
        serverManager.setGroupExists(group -> serverGroupManager.existServerGroup(group.getName()));
        templateSync.start();
        playerManager = new PlayerManager();
        serverJarManager = new ServerJarManager();

        try {
            tls = MasterTls.load(configPath.resolve("tls"));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not load or create the TLS certificate in config/tls: " + e.getMessage(), e);
        }
        Console.info("TLS fingerprint of the master: %s", tls.getFingerprint());
        new Thread(new WrapperServer(masterConfig.host(), masterConfig.port(), tls.getSslContext())).start();
        eventManager = new EventManager();
        new ModuleManager(eventManager, new MasterModuleApi());
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Console.debug("Writing server stats...");
                double cpu = 0;
                long usedMem = 0;
                long freeMem = 0;
                for (var wrapper : getWrapperManager().getWrappers().values()) {
                    cpu += wrapper.getCpuUsage();
                    usedMem += wrapper.getUsedMemory() / (1024 * 1024);
                    freeMem += wrapper.getFreeMemory() / (1024 * 1024);
                }
                // No wrapper would give NaN, which the database rejects
                cpu = getWrapperManager().getWrappers().isEmpty() ? 0 : cpu / getWrapperManager().getWrappers().size();
                var onlineServer = getServerManager().getServers().stream().filter(AbstractServer::isOnline).toList()
                        .size();
                var startedServers = getServerManager().getServers().size();
                var onlinePlayers = getPlayerManager().getPlayers().size();
                var cloudStats = new CloudStats(0, new Timestamp(System.currentTimeMillis()), cpu, usedMem, freeMem,
                        onlineServer, startedServers, onlinePlayers);
                databaseConnection.getObjectHandler().insertObject(cloudStats).whenComplete((saved, e) -> {
                    if (e != null) {
                        Console.error("Could not save the cloud stats: %s", rootMessage(e));
                    } else {
                        Console.debug("Saved server stats.");
                    }
                });
            }
        }, Math.max(0, 60000 - Calendar.getInstance().get(Calendar.SECOND) * 1000L
                + Calendar.getInstance().get(Calendar.MILLISECOND)), 60000);

        // TODO: api methode for saving temp to template
        // TODO: master command to save temp to template
    }

    /**
     * Asks the first-start questions and writes their answers: master.json, database.json (if asked) and the chosen
     * official modules, which are then loaded with the others. Ctrl+C cancels without saving anything.
     */
    private void runFirstStartSetup(boolean askDatabase) {
        var modules = new ModuleUpdater(MODULES);
        var offered = OfficialModule.ALL.stream().filter(module -> !modules.isInstalled(module)).toList();
        FirstStartSetup.Result result;
        try {
            result = new FirstStartSetup(new ConsoleSetupIo(), new ServerDatabaseCheck()).run(askDatabase, offered);
        } catch (Console.AskCancelledException e) {
            Console.info("Setup cancelled, nothing was saved. Start the master again to run it.");
            // Nothing to stop yet
            stopped.set(true);
            console.stopLogging();
            System.exit(0);
            return;
        }
        var config = new MasterConfig();
        config.port(result.port());
        config.forwardingMode(result.forwardingMode());
        config.completeDefaults();
        JsonConfiguration.saveConfiguration(configPath.resolve("master.json"), config);
        if (result.database() != null) {
            JsonConfiguration.saveConfiguration(configPath.resolve("database.json"), result.database());
        }
        for (var module : result.modules()) {
            try {
                modules.install(module, version);
                Console.info("Installed the %s", module.name());
            } catch (IOException e) {
                Console.warn("Could not install the %s: %s. Install it later with \"modules install %s\".",
                        module.name(), ModuleUpdater.describe(e), module.name());
            }
        }
        Console.info("Setup done. Next: start a wrapper with \"java -jar launcher.jar --wrapper\" and register it "
                + "here with the \"wrapper add\" command it prints.");
    }

    /**
     * Changes messages, saves them and sends them to all wrappers, which pass them on to their servers.
     */
    public synchronized void updateMessages(Map<MessageKey, String> changes) {
        pluginMessageConfig.messages().putAll(changes);
        JsonConfiguration.saveConfiguration(configPath.resolve("messages.json"), pluginMessageConfig);
        wrapperManager.getWrappers().values().forEach(Wrapper::sendMessages);
    }

    private static String rootMessage(Throwable throwable) {
        while (throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable.getMessage();
    }

    /**
     * Saves the maintenance state and pushes it to every proxy.
     */
    public void setMaintenance(boolean active) {
        masterConfig.maintenance(active);
        JsonConfiguration.saveConfiguration(configPath.resolve("master.json"), masterConfig);
        serverManager.getServersByType(ServerType.PROXY).stream()
                .map(Server::getWrapper).distinct().filter(Objects::nonNull).forEach(wrapper ->
                        wrapper.sendPacket(new PacketContainer(PacketType.MAINTENANCE_CHANGE, new MaintenanceModeChangePacket(active))));
    }

    /**
     * Backs up the embedded database into database/backups/ ("database backup"); older backups are kept.
     */
    public Path backupDatabase() throws IOException {
        return DatabaseTools.backup(databaseConnection, Path.of("database", "backups"), Integer.MAX_VALUE);
    }

    public boolean isStopping() {
        return stopped.get();
    }

    /**
     * Right after the master started, new servers wait until all registered wrappers are back (at most a minute):
     * servers that kept running on them are taken over instead of being started a second time.
     */
    public boolean isWaitingForWrappers() {
        if (System.currentTimeMillis() - startedAt > WRAPPER_WAIT_MILLIS) {
            return false;
        }
        return wrapperManager.getWrappers().size() < wrapperManager.getWrapperRegistry().size();
    }

    /**
     * Runs once, from the shutdown hook. Wrappers stop their servers as soon as the master disconnects (static
     * servers save their worlds first) and connect again when it is back.
     */
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        Console.info("Stopping the master...");
        for (var wrapper : List.copyOf(wrapperManager.getWrappers().values())) {
            var channel = wrapper.getChannel();
            if (channel != null) {
                // Wait for the close to go out, the process ends right after this
                channel.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            }
        }
        runSafely("stop the modules", () -> ModuleManager.getInstance().stopModules());
        runSafely("close the database connection", databaseConnection::close);
        console.stopLogging();
    }

    private static void runSafely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Console.error("Could not %s: %s", what, e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        new RexlCloudMaster(args);
    }

}
