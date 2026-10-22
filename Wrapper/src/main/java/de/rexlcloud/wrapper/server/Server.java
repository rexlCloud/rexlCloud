package de.rexlcloud.wrapper.server;

import com.google.gson.JsonObject;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.console.LogList;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerConsolePacket;
import de.rexlcloud.core.network.packets.ServerInfoPacket;
import de.rexlcloud.core.network.packets.ServerPortInUsePacket;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.core.server.ProxyForwarding;
import de.rexlcloud.core.server.ServerLifecycle;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.release.CloudReleases;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.wrapper.RexlCloudWrapper;
import de.rexlcloud.wrapper.server.process.LocalServerProcess;
import de.rexlcloud.wrapper.server.process.ServerProcess;
import de.rexlcloud.wrapper.server.process.TmuxServerProcess;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Server extends AbstractServer {

    /**
     * The wrapper reads and writes the server console as UTF-8, so the server has to use it as well instead of the
     * platform encoding (e.g. Cp1252 on Windows), otherwise characters like 'ü' get lost.
     */
    private static final String UTF8_CONSOLE = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 "
            + "-Dstdin.encoding=UTF-8 -Dorg.jline.terminal.encoding=UTF-8 ";

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[;\\d]*m");

    private static final long STOP_TIMEOUT_SECONDS = 120;
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private ServerProcess process;
    /**
     * When the current process started, for telling crashes right after the start apart.
     */
    private long startedAt;
    @Getter
    private File serverFolder;
    /**
     * Secret for this run, in cloud.json: the plugin proves with it that it belongs to this server.
     */
    private String connectToken = newConnectToken();
    @Getter
    private boolean writeLog = false;
    private final LogList logList;
    private final boolean maintenance;
    private final ProxyForwarding forwarding;
    private String templateVariant = "default";
    @Getter(AccessLevel.PRIVATE)
    private final CompletableFuture<AbstractServer> stopFuture = new CompletableFuture<>();

    public Server(String name, ServerGroup group, int port, UUID key, boolean maintenance, ProxyForwarding forwarding) {
        super(name, group, port, key);
        this.maintenance = maintenance;
        this.forwarding = forwarding;
        logList = new LogList();
    }

    public void setWriteLog(boolean writeLog) {
        this.writeLog = writeLog;
        if (writeLog) {
            var logs = new ArrayList<>(logList);
            Collections.reverse(logs);
            logs.stream().limit(10).forEach(Console::write);
        }
    }

    public String[] getLogs() {
        return logList.toArray();
    }

    @Override
    public void start() {
        if (!moveTo(ServerLifecycle.STARTING)) {
            return;
        }
        connectToken = newConnectToken();
        if (!waitForPort(getPort())) {
            // Something else (another program, a server that did not stop) has the port: the master picks another
            Console.warn("Port %s for %s is already in use on this wrapper", getPort(), getName());
            var channel = RexlCloudWrapper.getInstance().getWrapperClient().getChannel();
            if (channel != null) {
                channel.writeAndFlush(new PacketContainer(PacketType.SERVER_PORT_IN_USE,
                        new ServerPortInUsePacket(getName(), getKey(), getPort())));
            }
            moveTo(ServerLifecycle.STOPPING);
            complete(false);
            return;
        }
        createServerFiles();
        var serverJar = getGroup().getType() == ServerType.SERVER ? "server.jar" : "proxy.jar";
        var jar = new File(serverFolder, serverJar);
        if (!jar.exists()) {
            if (getGroup().isCustomJar()) {
                waitForCustomJar();
            } else {
                failStart("there is no " + serverJar);
            }
            return;
        }
        var javaCommand = getGroup().getJavaCommand() == null || getGroup().getJavaCommand().isBlank()
                ? "java" : getGroup().getJavaCommand().trim();
        if (new File(javaCommand).isAbsolute() && !new File(javaCommand).canExecute()) {
            failStart("the Java installation " + javaCommand + " is missing on this wrapper");
            return;
        }
        try {
            // A list instead of one string, so paths with spaces work
            var startParams = new ArrayList<String>();
            startParams.add(javaCommand);
            startParams.add("-Xmx" + getGroup().getMemory() + "m");
            startParams.add("-Xms256m");
            startParams.addAll(List.of(UTF8_CONSOLE.trim().split("\\s+")));
            if (getGroup().getType() == ServerType.SERVER && !getGroup().isCustomJar()) {
                startParams.add("-Dcom.mojang.eula.agree=true");
            }
            var javaArgs = getGroup().getJavaArguments() == null ? "" : getGroup().getJavaArguments().trim();
            if (!javaArgs.isEmpty()) {
                startParams.addAll(List.of(javaArgs.split("\\s+")));
            }
            startParams.addAll(List.of("-jar", serverJar));
            if (getGroup().isCustomJar()) {
                // Its own main decides; port and forwarding are in cloud.json
            } else if (getGroup().getType() == ServerType.SERVER) {
                // Spelled so every version understands them: Minecraft 1.8 has no --port and no --nogui
                startParams.addAll(List.of("--online-mode", "false", "-p", String.valueOf(getPort()), "nogui"));
            } else {
                if (ForwardingSetup.isVelocity(jar.toPath())) {
                    startParams.addAll(List.of("-port", String.valueOf(getPort())));
                }
                // BungeeCord rejects unknown arguments; its port is in config.yml
            }
            process = launch(startParams);
        } catch (IOException e) {
            failStart(e.getMessage());
            return;
        }
        sendServerInfo();
        watch(process, System.currentTimeMillis());
    }

    /**
     * Starts the server's Java process: in a tmux session if the wrapper keeps its servers running across
     * restarts ("detachedServers"), otherwise as a child process.
     */
    private ServerProcess launch(List<String> command) throws IOException {
        var wrapper = RexlCloudWrapper.getInstance();
        if (!wrapper.isDetachedServers()) {
            return LocalServerProcess.start(serverFolder, command);
        }
        var session = getName() + "-" + getKey().toString().substring(0, 8);
        TmuxServerProcess started;
        try {
            started = TmuxServerProcess.start(wrapper.getTmuxSocket(), session, serverFolder.toPath(), command);
        } catch (RuntimeException e) {
            throw new IOException("tmux: " + e.getMessage(), e);
        }
        wrapper.getRunningServers().add(new RunningServers.Entry(getName(), getKey(), getGroup(), getPort(), maintenance,
                forwarding, serverFolder.getPath(), templateVariant, connectToken, System.currentTimeMillis(), session));
        return started;
    }

    /**
     * Continues with a server that kept running in its tmux session while the wrapper restarted; its plugin connects
     * again by itself.
     */
    public void attach(RunningServers.Entry entry, TmuxServerProcess running) {
        serverFolder = new File(entry.folder());
        connectToken = entry.connectToken();
        templateVariant = entry.templateVariant();
        process = running;
        moveTo(ServerLifecycle.STARTING);
        watch(running, entry.startedAt());
    }

    /**
     * Reports the process's end and passes its console on.
     */
    private void watch(ServerProcess launched, long since) {
        startedAt = since;
        launched.exit().thenAccept(code -> onProcessExit(launched, code));
        Thread.ofPlatform().name("console-" + getName()).daemon().start(() -> {
            try {
                launched.readOutput(line -> {
                    logList.add(line);
                    // Attached servers are read before the connection to the master exists
                    var client = RexlCloudWrapper.getInstance().getWrapperClient();
                    var channel = client == null ? null : client.getChannel();
                    if (channel != null) {
                        channel.writeAndFlush(new PacketContainer(PacketType.SERVER_CONSOLE_LOG,
                                new ServerConsolePacket(getName(), line, System.currentTimeMillis())));
                    }
                    if (isWriteLog()) {
                        Console.write(line);
                    }
                });
            } catch (IOException e) {
                Console.debug("The console of %s ended: %s", getName(), e.getMessage());
            }
        });
    }

    /**
     * The process ended: cleaned up right away after a stop; after a crash with a growing delay, because the master
     * then starts a replacement and a server that keeps crashing would otherwise restart in a tight loop.
     */
    private void onProcessExit(ServerProcess exited, int exitCode) {
        if (process != exited) {
            return;
        }
        var wasOnline = isOnline();
        if (moveTo(ServerLifecycle.STOPPING)) {
            // Also for servers that stop themselves: one that does so right after every start loops like a crash
            var delay = CrashBackoff.delayMillis(getGroup().getName(), System.currentTimeMillis() - startedAt);
            var reported = delay > 0 ? ", reported in " + delay / 1000 + " seconds" : "";
            if (isCleanExit(wasOnline, exitCode)) {
                // e.g. "stop" in its console
                Console.info("The server %s shut itself down%s", getName(), reported);
            } else {
                var lines = logList.snapshot();
                var lastLine = lines.isEmpty() ? "" : ANSI.matcher(lines.getLast()).replaceAll("");
                Console.error("The server %s crashed (exit code %s)%s: %s", getName(), exitCode, reported,
                        lastLine);
            }
            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> complete(true));
        } else {
            // Stopped on purpose; give the process a moment to release its files
            CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS).execute(() -> complete(true));
        }
    }

    /**
     * A static server of a custom jar group whose jar the user has not put on this wrapper yet: not an error, it is
     * tried again later, with the same growing delay as failed starts so it does not loop every few seconds.
     */
    private void waitForCustomJar() {
        moveTo(ServerLifecycle.STOPPING);
        var delay = CrashBackoff.delayMillis(getGroup().getName(), 0);
        Console.warn("%s waits for its server.jar: put it into servers/templates/%s/default/ on this wrapper "
                + "(checked again in %s seconds)", getName(), getGroup().getName(), delay / 1000);
        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> complete(true));
    }

    /**
     * The server could not be started; the master is told after the same delay as for a crash.
     */
    private void failStart(String reason) {
        Console.error("The server %s could not be started: %s!", getName(), reason);
        moveTo(ServerLifecycle.STOPPING);
        var delay = CrashBackoff.delayMillis(getGroup().getName(), 0);
        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> complete(true));
    }

    /**
     * Cleans up after the process is gone and, if {@code report}, tells the master, which then starts the server
     * again or removes it.
     */
    private void complete(boolean report) {
        if (getLifecycle() != ServerLifecycle.STOPPING) {
            return;
        }
        deleteFiles();
        RexlCloudWrapper.getInstance().getRunningServers().remove(getName(), getKey());
        var channel = RexlCloudWrapper.getInstance().getWrapperClient().getChannel();
        if (report && channel != null) {
            channel.writeAndFlush(new PacketContainer(PacketType.SERVER_STOP, new ServerStopPacket(getName(), getKey())));
        }
        onlinePlayers.clear();
        moveTo(ServerLifecycle.STOPPED);
        RexlCloudWrapper.getInstance().getServerManager().getServers().remove(this);
        stopFuture.complete(this);
    }

    public void sendServerInfo() {
        if (RexlCloudWrapper.getInstance().getWrapperClient().getChannel() != null) {
            var serverInfoPacket = new ServerInfoPacket(getName(), isOnline(), getState());
            var packetContainer = new PacketContainer(PacketType.SERVER_INFO, serverInfoPacket);
            RexlCloudWrapper.getInstance().getWrapperClient().getChannel().writeAndFlush(packetContainer);
        }
    }

    /**
     * A static server keeps its files, but gets the jar installed for its group if it has none yet.
     */
    private void copyStaticServerJar() {
        var jarName = getGroup().getType() == ServerType.SERVER ? "server.jar" : "proxy.jar";
        var jar = new File(serverFolder, jarName);
        var installed = new File("servers/templates/" + getGroup().getName() + "/default", jarName);
        if (!jar.exists() && installed.exists()) {
            try {
                Files.copy(installed.toPath(), jar.toPath());
            } catch (IOException e) {
                Console.error("The %s for %s could not be copied: %s", jarName, getName(), e.getMessage());
            }
        }
    }

    /**
     * Paper reads -Dcom.mojang.eula.agree, Spigot only eula.txt.
     */
    private void acceptEula() {
        try {
            Files.writeString(new File(serverFolder, "eula.txt").toPath(), "eula=true\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            Console.error("eula.txt for %s could not be written: %s", getName(), e.getMessage());
        }
    }

    private void createServerFiles() {
        if (getGroup().isStaticServer()) {
            serverFolder = new File("servers/static/" + getName());
        } else {
            serverFolder = new File("servers/temp/" + getName() + "_" + getKey());
        }
        serverFolder.mkdirs();
        if (!getGroup().isStaticServer()) {
            // Layers, each overriding the one before: global template, default/, then one random variant
            copyGlobalTemplate(true);
            var groupTemplates = new File("servers/templates/" + getGroup().getName());
            var defaultTemplate = new File(groupTemplates, "default");
            defaultTemplate.mkdirs();
            copyTemplate(defaultTemplate, true);
            randomVariant(groupTemplates).ifPresent(variant -> {
                templateVariant = variant.getName();
                copyTemplate(variant, true);
            });

        } else {
            // A static server keeps its own files; global files only fill in what it does not have
            copyGlobalTemplate(false);
            copyStaticServerJar();
            var lofFolder = new File(serverFolder + "/logs");
            if (lofFolder.exists()) {
                try {
                    FileUtils.deleteDirectory(lofFolder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        createCloudInfo();
        if (getGroup().isCustomJar()) {
            // Not Paper/Spigot: no EULA file, no Paper configuration and no cloud plugin
            return;
        }
        if (getGroup().getType() == ServerType.SERVER) {
            acceptEula();
        }
        try {
            if (getGroup().getType() == ServerType.SERVER) {
                ForwardingSetup.configureServer(serverFolder.toPath(), getName(), forwarding);
                // Minecraft 26 turns the whitelist on in new worlds; players come through the proxy instead.
                // A template that sets white-list itself keeps its value.
                ForwardingSetup.setPropertyIfAbsent(serverFolder.toPath().resolve("server.properties"), "white-list", "false");
            } else {
                ForwardingSetup.configureProxy(serverFolder.toPath(), getName(), forwarding);
                if (!ForwardingSetup.isVelocity(serverFolder.toPath().resolve("proxy.jar"))) {
                    ForwardingSetup.configureBungeeCordListener(serverFolder.toPath(), getPort());
                }
            }
        } catch (IOException e) {
            Console.error("Forwarding for %s could not be set up: %s", getName(), e.getMessage());
        }
        addCloudPlugin();
    }

    private void addCloudPlugin() {
        FileFilter fileFilter = new WildcardFileFilter(
                getGroup().getType() == ServerType.PROXY ? "proxy-*.jar" : "bridge-*.jar");
        var pluginsFolder = new File(serverFolder + "/plugins/");
        pluginsFolder.mkdir();
        final var files = pluginsFolder.listFiles(fileFilter);
        if (files != null) {
            for (var file : files) {
                try {
                    FileUtils.delete(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        var version = RexlCloudWrapper.getInstance().getVersion();
        var type = getGroup().getType() == ServerType.PROXY ? "proxy" : "bridge";
        var fileName = type + "-" + version + ".jar";
        // Downloaded once per version, not for every server start
        var cached = Path.of("cache", "plugins", fileName);
        try {
            if (!Files.isRegularFile(cached)) {
                // bridge.jar or proxy.jar of the cloud's GitHub release, checked against its SHA-256
                CloudReleases.official().download(version, type, cached);
            }
            Files.copy(cached, new File(pluginsFolder, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            Console.error("The cloud plugin %s could not be installed, %s starts without a connection to the cloud: %s",
                    fileName, getName(), e.getMessage());
        }
    }

    private void copyGlobalTemplate(boolean overwrite) {
        var globalTemplate = getGroup().getType() == ServerType.SERVER ? new File("servers/templates/global/server")
                : new File("servers/templates/global/proxy");
        globalTemplate.mkdirs();
        copyTemplate(globalTemplate, overwrite);
    }

    /**
     * Whether a server can listen on the port (on all addresses, like Minecraft servers and proxies do). The
     * default socket options match theirs, so closed connections still waiting in TIME_WAIT do not count.
     */
    static boolean isPortFree(int port) {
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Waits up to ten seconds for the port, e.g. while the server that had it before is still shutting down.
     */
    private static boolean waitForPort(int port) {
        for (int attempt = 0; attempt < 10; attempt++) {
            if (isPortFree(port)) {
                return true;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isPortFree(port);
    }

    /**
     * Template variants are the folders next to default/, e.g. one per map. Each new server gets a random one.
     */
    static Optional<File> randomVariant(File groupTemplates) {
        var variants = groupTemplates.listFiles(file -> file.isDirectory() && !file.getName().equals("default"));
        if (variants == null || variants.length == 0) {
            return Optional.empty();
        }
        return Optional.of(variants[ThreadLocalRandom.current().nextInt(variants.length)]);
    }

    /**
     * Copies a template into the server folder, including sub-folders such as plugins/.
     *
     * @param overwrite whether files the server folder already has are replaced
     */
    private void copyTemplate(File template, boolean overwrite) {
        var source = template.toPath();
        var target = serverFolder.toPath();
        try (var files = Files.walk(source)) {
            for (var file : (Iterable<Path>) files::iterator) {
                var destination = target.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(destination);
                } else if (overwrite || !Files.exists(destination)) {
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException e) {
            Console.error("The template %s could not be copied to %s: %s", template, getName(), e.getMessage());
        }
    }

    private void createCloudInfo() {
        var jsonObject = new JsonObject();
        jsonObject.addProperty("ServerName", getName());
        jsonObject.addProperty("ServerPort", getPort());
        jsonObject.addProperty("ServerKey", getKey().toString());
        jsonObject.addProperty("WrapperToken", connectToken);
        jsonObject.addProperty("Maintenance", maintenance);
        // Where the plugin reaches this wrapper
        jsonObject.addProperty("WrapperPort", RexlCloudWrapper.getInstance().getWrapperConfig().getServerPort());
        // Which template variant (e.g. map) the server got, "default" if the group has none
        jsonObject.addProperty("TemplateVariant", templateVariant);
        if (getGroup().isCustomJar() && forwarding != null) {
            // A custom server sets up player forwarding itself (the MinestomBridge does)
            jsonObject.addProperty("ForwardingMode", forwarding.mode().name());
            jsonObject.addProperty("ForwardingSecret", forwarding.secret());
        }
        var cloudJson = serverFolder.toPath().resolve("cloud.json");
        JsonConfiguration.save(cloudJson, JsonConfiguration.GSON().toJson(jsonObject));
        try {
            // Other users on the machine must not read the token
            Files.setPosixFilePermissions(cloudJson, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows: no POSIX permissions
        }
    }

    @Override
    public CompletableFuture<AbstractServer> stop() {
        if (getLifecycle() == ServerLifecycle.WAITING) {
            moveTo(ServerLifecycle.STOPPED);
            RexlCloudWrapper.getInstance().getServerManager().getServers().remove(this);
            stopFuture.complete(this);
            return stopFuture;
        }
        if (!moveTo(ServerLifecycle.STOPPING)) {
            return stopFuture;
        }
        sendServerInfo();
        if (process == null) {
            complete(true);
            return stopFuture;
        }
        if (getGroup().isStaticServer() && getChannel() != null) {
            // Static servers save their world: the plugin shuts the server down
            getChannel().writeAndFlush(new PacketContainer(PacketType.SERVER_STOP, null));
        } else {
            process.terminate();
        }
        var stopping = process;
        CompletableFuture.delayedExecutor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (stopping.isAlive()) {
                Console.warn("%s did not exit after %s seconds and is killed", getName(), STOP_TIMEOUT_SECONDS);
                stopping.kill();
            }
        });
        return stopFuture;
    }

    /**
     * Kills the server process immediately instead of waiting for a graceful shutdown.
     */
    public void kill() {
        if (process == null || !process.isAlive()) {
            stop();
            return;
        }
        if (moveTo(ServerLifecycle.STOPPING)) {
            sendServerInfo();
        }
        process.kill();
    }

    /**
     * Kills the process and removes its files right away, when the wrapper shuts down.
     */
    public void forceStop() {
        moveTo(ServerLifecycle.STOPPING);
        if (process != null) {
            process.kill();
            try {
                process.exit().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) {
                // gone or not; the files are removed either way
            }
        }
        RexlCloudWrapper.getInstance().getRunningServers().remove(getName(), getKey());
        deleteFiles();
    }

    private static String newConnectToken() {
        var bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * A server that was online and exited with code 0 stopped itself (e.g. "stop" in its console); anything else
     * is a crash, including a clean exit before it ever came online.
     */
    static boolean isCleanExit(boolean wasOnline, int exitCode) {
        return wasOnline && exitCode == 0;
    }

    /**
     * This run's token, as written into cloud.json.
     */
    String getConnectToken() {
        return connectToken;
    }

    /**
     * Whether the token a connecting plugin sent is this run's.
     */
    public boolean checkConnectToken(String token) {
        return token != null && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                connectToken.getBytes(StandardCharsets.UTF_8));
    }

    public void deleteFiles() {
        // No folder yet if the start failed before the files were created
        if (serverFolder != null && !getGroup().isStaticServer()) {
            try {
                FileUtils.deleteDirectory(serverFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void execute(String command) {
        if (process == null) {
            return;
        }
        try {
            process.send(command);
        } catch (IOException e) {
            Console.warn("The command could not be sent to %s: %s", getName(), e.getMessage());
        }
    }
}
