package de.rexlcloud.master.wrapper;

import com.google.gson.reflect.TypeToken;
import de.rexlcloud.core.api.events.events.wrapper.WrapperDisconnectedEvent;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.server.Server;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import de.rexlcloud.core.server.ServerLifecycle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WrapperManager {

    @Getter
    private final HashMap<String, Wrapper> wrappers;
    @Getter
    private final HashMap<String, WrapperCredentials> wrapperRegistry;
    private Path wrapperConfigPath = Path.of("config");

    public WrapperManager() {
        wrappers = new HashMap<>();
        wrapperRegistry = new HashMap<>();
        //wrapperRegistry.put("test", new WrapperCredentials("test", "127.0.0.1", UUID.fromString("b41231ad-e597-4fd9-afb0-74187f942824")));
        JsonConfiguration.createFolder(wrapperConfigPath);
        wrapperConfigPath = wrapperConfigPath.resolve("wrappers.json");
        if (!JsonConfiguration.existsConfiguration(wrapperConfigPath)) {
            JsonConfiguration.save(wrapperConfigPath, JsonConfiguration.GSON().toJson(wrapperRegistry.values()));
        } else {
            ArrayList<WrapperCredentials> load = JsonConfiguration.load(wrapperConfigPath, new TypeToken<ArrayList<WrapperCredentials>>() {
            }.getType());
            if (load != null) {
                load.forEach(this::addWrapperCredentials);
            }
        }
    }

    @NonNull
    public UUID getWrapperKey(@NonNull Wrapper wrapper) {
        return wrapperRegistry.get(wrapper.getName()).getKey();
    }

    public boolean addWrapperCredentials(WrapperCredentials credentials) {
        if (!wrapperRegistry.containsKey(credentials.getName())) {
            wrapperRegistry.put(credentials.getName(), credentials);
            JsonConfiguration.save(wrapperConfigPath, JsonConfiguration.GSON().toJson(wrapperRegistry.values()));
            return true;
        } else {
            Console.error("A wrapper with the same name is already registered!");
            return false;
        }
    }

    public String getNewWrapperName() {
        return getWrapperName(1);
    }

    private String getWrapperName(int startIndex) {
        if (!wrapperRegistry.containsKey("Wrapper-" + startIndex)) {
            return "Wrapper-" + startIndex;
        }
        return getWrapperName(startIndex + 1);
    }

    /**
     * Registers an authenticated connection. An older connection of the same wrapper that the master has not
     * noticed as dead yet (e.g. after a network outage) is replaced.
     */
    public void registerWrapper(Wrapper wrapper) {
        var old = wrappers.get(wrapper.getName());
        if (old != null) {
            Console.warn("%s connected again, closing its old connection", wrapper.getName());
            unregisterWrapper(old);
            old.getChannel().close();
        }
        wrappers.put(wrapper.getName(), wrapper);
    }

    /**
     * How long the servers of a wrapper whose connection broke are kept: if it connects again in time, it reports
     * them and they continue as if nothing happened.
     */
    private static final long RECONNECT_GRACE_SECONDS = 120;
    private final ScheduledExecutorService graceTimer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("wrapper-reconnect-grace").daemon().factory());

    private static final long MEGABYTE = 1024L * 1024L;
    /**
     * A server that just came online has not taken its full memory yet; it counts as reserved this long.
     */
    private static final long WARMUP_MILLIS = 60_000;
    private final Map<String, Long> lastMemoryWarning = new ConcurrentHashMap<>();

    /**
     * The wrapper with the most free memory that can run the server: connected, templates synced, not shutting
     * down, with the group's Java runtime and enough memory for it. Wrappers with a busy CPU only if no other fits.
     *
     * @return {@code null} if none can take it right now; the server is tried again a few seconds later
     */
    public Wrapper getWrapperForServer(Server server) {
        var group = server.getGroup();
        var templateSync = RexlCloudMaster.getInstance().getTemplateSync();
        var candidates = wrappers.values().stream()
                // Not before a wrapper has the templates
                .filter(templateSync::isReady)
                .filter(wrapper -> !wrapper.isInShutdown())
                // Only wrappers that have the Java runtime of the group
                .filter(wrapper -> wrapper.canRun(group))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        // The server itself is starting already and may still point to the wrapper of its last run
        ToLongFunction<Wrapper> free = wrapper -> getMemoryForNewServers(wrapper, server);
        var needed = group.getMemory() * MEGABYTE;
        var chosen = WrapperChoice.choose(candidates, free, Wrapper::getCpuUsage, needed);
        if (chosen.isEmpty()) {
            warnNoRoom(server, "any wrapper", candidates.stream().mapToLong(free).max().orElse(0));
            return null;
        }
        return chosen.get();
    }

    /**
     * The wrapper of a static server, if it is connected and has room for it.
     */
    @Nullable
    public Wrapper getWrapperForStaticServer(Server server) {
        var wrapper = getWrapperByName(server.getGroup().getWrapper());
        if (wrapper == null) {
            return null;
        }
        var free = getMemoryForNewServers(wrapper, server);
        if (free < server.getGroup().getMemory() * MEGABYTE) {
            warnNoRoom(server, wrapper.getName(), free);
            return null;
        }
        return wrapper;
    }

    /**
     * At most once a minute per group.
     */
    private void warnNoRoom(Server server, String where, long mostFree) {
        var group = server.getGroup();
        var now = System.currentTimeMillis();
        var last = lastMemoryWarning.getOrDefault(group.getName(), 0L);
        if (now - last > 60_000) {
            lastMemoryWarning.put(group.getName(), now);
            Console.warn("Not enough memory for %s on %s: it needs %s MB, %s MB are free; the server waits",
                    server.getName(), where, group.getMemory(), Math.max(0, mostFree / MEGABYTE));
        }
    }

    /**
     * Bytes a new server can get on the wrapper: its available memory minus what servers that are starting (or
     * just started) there will still take.
     */
    public long getMemoryForNewServers(Wrapper wrapper) {
        return getMemoryForNewServers(wrapper, null);
    }

    private long getMemoryForNewServers(Wrapper wrapper, Server except) {
        var reserved = RexlCloudMaster.getInstance().getServerManager().getServers().stream()
                .filter(server -> server != except && server.getWrapper() == wrapper)
                .filter(server -> server.isStarting() || (server.isOnline() && server.getMillisInLifecycle() < WARMUP_MILLIS))
                .mapToLong(server -> server.getGroup().getMemory() * MEGABYTE)
                .sum();
        return wrapper.getFreeMemory() - reserved;
    }

    /**
     * Whether groups may run servers with this java command: "java" from PATH or a runtime a connected wrapper
     * reported. Nothing else is accepted, because the wrappers execute it.
     */
    public boolean isKnownJavaCommand(String javaCommand) {
        return javaCommand.equals("java") || wrappers.values().stream().anyMatch(wrapper ->
                wrapper.getJavaRuntimes().stream().anyMatch(runtime -> runtime.executable().equals(javaCommand)));
    }

    /**
     * Executables of the Java runtimes reported by the connected wrappers.
     */
    public List<String> getJavaExecutables() {
        return wrappers.values().stream().flatMap(wrapper -> wrapper.getJavaRuntimes().stream())
                .map(runtime -> runtime.executable()).distinct().sorted().toList();
    }

    public Optional<Wrapper> getWrapperFromApiWrapper(de.rexlcloud.core.api.Wrapper wrapper) {
        return wrappers.values().stream().filter(wrapper1 -> wrapper1.getName().equals(wrapper.getName())).findAny();
    }

    /**
     * The wrapper's connection is gone. A wrapper that shut down took its servers with it; otherwise they keep
     * running on it and are kept for {@link #RECONNECT_GRACE_SECONDS} in case it connects again.
     */
    public void unregisterWrapper(@Nullable Wrapper wrapper) {
        if (wrapper == null || !wrappers.remove(wrapper.getName(), wrapper)) {
            return;
        }
        wrapper.disconnected();
        if (RexlCloudMaster.getInstance().isStopping()) {
            // The master closes the connections itself; the wrappers keep their servers for its next start
            return;
        }
        RexlCloudMaster.getInstance().getTemplateSync().onDisconnect(wrapper);
        RexlCloudMaster.getInstance().getEventManager().callEvent(new WrapperDisconnectedEvent(wrapper.getApiWrapper()));
        var hasServers = RexlCloudMaster.getInstance().getServerManager().getServers().stream()
                .anyMatch(server -> server.getWrapper() == wrapper && server.getLifecycle() != ServerLifecycle.WAITING);
        if (wrapper.isInShutdown() || !hasServers) {
            Console.info("%s disconnected", wrapper.getName());
            wrapper.releaseServers();
            return;
        }
        Console.warn("%s disconnected; its servers are kept for %s seconds in case it comes back", wrapper.getName(),
                RECONNECT_GRACE_SECONDS);
        graceTimer.schedule(() -> {
            // Servers it reported after reconnecting belong to the new connection by now
            wrapper.releaseServers();
        }, RECONNECT_GRACE_SECONDS, TimeUnit.SECONDS);
    }

    public void stopWrapper(@Nullable Wrapper wrapper) {
        if (wrapper == null) return;
        wrapper.stop();
        Console.info("Stopping the wrapper %s", wrapper.getName());
    }

    public void removeWrapper(Wrapper wrapper) {
        wrappers.remove(wrapper.getName());
    }

    public boolean canWrapperRegister(String address, UUID key) {
        return wrapperRegistry.values().stream().anyMatch(credentials -> credentials.checkCredentials(address, key));
    }

    @Nullable
    public Wrapper getWrapperByName(String wrapperName) {
        return wrappers.getOrDefault(wrapperName, null);
    }

    public String getWrapperName(String address, UUID authKey) {
        return wrapperRegistry.values().stream().filter(credentials -> credentials.checkCredentials(address, authKey)).findFirst().orElseThrow(RuntimeException::new).getName();
    }

    public Optional<Wrapper> getWrapperByChannel(Channel channel) {
        return wrappers.values().stream().filter(wrapper -> wrapper.getChannel().equals(channel)).findFirst();
    }

}
