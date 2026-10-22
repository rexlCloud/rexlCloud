package de.rexlcloud.wrapper;

import de.rexlcloud.wrapper.server.process.TmuxServerProcess;
import de.rexlcloud.wrapper.server.RunningServers;
import de.rexlcloud.core.command.CommandRegistry;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.config.PluginMessageConfig;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.tls.PinnedTrustManager;
import de.rexlcloud.core.server.ServerManager;
import de.rexlcloud.wrapper.commands.Commands;
import de.rexlcloud.wrapper.config.WrapperConfig;
import de.rexlcloud.wrapper.network.WrapperClient;
import de.rexlcloud.wrapper.server.JavaRuntimes;
import de.rexlcloud.wrapper.server.Server;
import de.rexlcloud.wrapper.server.ServerConnectionManager;
import de.rexlcloud.wrapper.server.TemplateStore;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Getter
public class RexlCloudWrapper {

    @Getter
    private static RexlCloudWrapper instance;
    private final Console console;
    private final CommandRegistry commandRegistry;
    @Setter
    private PluginMessageConfig pluginMessageConfig;
    private WrapperClient wrapperClient;
    private final WrapperConfig wrapperConfig;
    private final ServerManager<Server> serverManager;
    private final ServerConnectionManager serverConnectionManager;
    private final String version;
    private final JavaRuntimes javaRuntimes;
    private final TemplateStore templateStore;

    private Path configPath = Path.of("config");
    /**
     * Whether servers run in tmux sessions and outlive the wrapper (see {@link WrapperConfig#isDetachedServers()}).
     */
    private boolean detachedServers;
    private final RunningServers runningServers = new RunningServers(Path.of("servers", "running.json"));

    public RexlCloudWrapper(String[] args) throws IOException {
        // Libraries (HikariCP, Netty, Javalin) log through SLF4J: only their warnings and errors. Set here because the
        // launcher's class loader keeps simplelogger.properties out of reach.
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel",
                System.getProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn"));
        var arguments = Arrays.asList(args);
        // The launcher passes it; started by hand without it, the cloud plugins cannot be downloaded
        version = arguments.stream().filter(s -> s.startsWith("--version=")).map(s -> s.split("=")[1]).findAny()
                .orElse("UNKNOWN");
        instance = this;
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        pluginMessageConfig = new PluginMessageConfig();

        serverManager = new ServerManager<>(false);
        commandRegistry = new CommandRegistry();
        commandRegistry.addCommand(new Commands());

        console = new Console(commandRegistry);

        if (arguments.contains("--debug")) {
            console.setDebug();
        }

        new Thread(console).start();

        JsonConfiguration.createFolder(configPath);
        configPath = configPath.resolve("wrapper.json");
        wrapperConfig = JsonConfiguration.loadOrCreateConfiguration(configPath, new WrapperConfig());
        if (wrapperConfig.getAuthKey() == null) {
            Console.info("No auth key found, creating a new one!");
            var authKey = UUID.randomUUID();
            wrapperConfig.setAuthKey(authKey);
            JsonConfiguration.saveConfiguration(configPath, wrapperConfig);
            Console.info("Register the wrapper on the master with this command: \"wrapper add %s %s\"", "127.0.0.1", authKey);
        }
        // Needs the config for its port
        serverConnectionManager = new ServerConnectionManager();
        new Thread(serverConnectionManager).start();
        templateStore = new TemplateStore(Path.of("servers", "templates"), wrapperConfig.getAuthKey());
        detachedServers = wrapperConfig.isDetachedServers();
        if (detachedServers && !TmuxServerProcess.available()) {
            Console.warn("detachedServers needs tmux, which is not installed; the servers run as child processes");
            detachedServers = false;
        }
        var attached = detachedServers ? attachRunningServers() : List.<File>of();
        removeLeftoverServers(attached);
        javaRuntimes = new JavaRuntimes(wrapperConfig.getJavaSearchFolders() == null ? List.of()
                : wrapperConfig.getJavaSearchFolders());
        javaRuntimes.get().forEach(runtime -> Console.info("Found Java: %s, %s", runtime.displayName(), runtime.executable()));

        initWrapper();
    }

    /**
     * Folders of temporary servers left behind when the wrapper was killed; no server runs from them any more.
     */
    /**
     * Servers that kept running in their tmux sessions while the wrapper was away continue; their plugins connect
     * again and the master takes them over.
     *
     * @return their folders
     */
    private List<File> attachRunningServers() {
        var folders = new java.util.ArrayList<File>();
        var stillRunning = new java.util.ArrayList<RunningServers.Entry>();
        for (var entry : runningServers.load()) {
            var running = TmuxServerProcess.attach(getTmuxSocket(), entry.session(), Path.of(entry.folder()));
            if (running.isEmpty()) {
                continue;
            }
            var server = new Server(entry.name(), entry.group(), entry.port(), entry.key(), entry.maintenance(),
                    entry.forwarding());
            serverManager.getServers().add(server);
            server.attach(entry, running.get());
            stillRunning.add(entry);
            folders.add(new File(entry.folder()).getAbsoluteFile());
        }
        runningServers.retain(stillRunning);
        if (!stillRunning.isEmpty()) {
            Console.info("Attached to %s server(s) that kept running", stillRunning.size());
        }
        return folders;
    }

    /**
     * The tmux socket of this wrapper; the plugin port keeps two wrappers on one machine apart.
     */
    public String getTmuxSocket() {
        return "rexlcloud-" + wrapperConfig.getServerPort();
    }

    private void removeLeftoverServers(List<File> keep) {
        var leftovers = new File("servers/temp").listFiles(folder -> folder.isDirectory()
                && !keep.contains(folder.getAbsoluteFile()));
        if (leftovers == null || leftovers.length == 0) {
            return;
        }
        for (var folder : leftovers) {
            try {
                FileUtils.deleteDirectory(folder);
            } catch (IOException e) {
                Console.warn("The old server folder %s could not be deleted: %s", folder, e.getMessage());
            }
        }
        Console.info("Deleted %s leftover server folder(s)", leftovers.length);
    }

    public void initWrapper() {
        SslContext sslContext;
        try {
            var trustManager = new PinnedTrustManager(wrapperConfig::getMasterFingerprint, fingerprint -> {
                // Trust on first use, like SSH: from now on only this master is accepted
                wrapperConfig.setMasterFingerprint(fingerprint);
                JsonConfiguration.saveConfiguration(configPath, wrapperConfig);
                Console.warn("The master is trusted from now on, its fingerprint was saved: %s. "
                        + "Compare it with the one the master shows on start.", fingerprint);
            });
            // The certificate is checked by its fingerprint; host names do not matter for a pinned, self-signed one
            sslContext = SslContextBuilder.forClient().trustManager(trustManager).protocols("TLSv1.3")
                    .endpointIdentificationAlgorithm(null).build();
        } catch (SSLException e) {
            throw new IllegalStateException("TLS is not available: " + e.getMessage(), e);
        }
        wrapperClient = new WrapperClient(wrapperConfig.getAddress(), wrapperConfig.getPort(), sslContext);
        wrapperClient.run();
    }

    public void stop() {
        if (detachedServers && !getServerManager().getServers().isEmpty()) {
            // "stop" stopped them already; otherwise (detach, systemd, an update) they keep running
            Console.info("Leaving %s server(s) running; the wrapper attaches to them when it starts again",
                    getServerManager().getServers().size());
            wrapperClient.close();
            console.stopLogging();
            return;
        }
        Console.info("Stopping the wrapper...");
        getServerManager().getServers().forEach(Server::forceStop);
        wrapperClient.close();
        try {
            FileUtils.deleteDirectory(new File("servers/temp"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        console.stopLogging();
    }

    public static void main(String[] args) throws IOException {
        new RexlCloudWrapper(args);
    }

}
