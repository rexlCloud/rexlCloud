package de.rexlcloud.core.server;

import de.rexlcloud.core.api.Server;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * The servers of the master or a wrapper. On the master it also starts waiting servers, notices servers that hang
 * while starting or stopping, and stops idle servers above a group's minimum.
 */
public class ServerManager<T extends AbstractServer> {

    private static final long IDLE_CHECK_SECONDS = 300;
    /**
     * A server that has not connected this long after its start is stopped (and started again if needed).
     */
    static final long START_TIMEOUT_MILLIS = 10 * 60_000;
    /**
     * A stop the wrapper did not confirm this long (it waits two minutes, then kills the process) is completed.
     */
    static final long STOP_TIMEOUT_MILLIS = 4 * 60_000;

    /**
     * All servers, including stopping ones until their stop is complete. Safe to iterate while it changes.
     */
    @Getter
    private final List<T> servers = new CopyOnWriteArrayList<>();
    /**
     * Whether a group still exists; servers of deleted groups are never kept for a minimum.
     */
    @Setter
    private Predicate<ServerGroup> groupExists = group -> true;

    public ServerManager(boolean isMaster) {
        if (isMaster) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().name("server-scheduler").daemon().factory());
            scheduler.scheduleWithFixedDelay(this::tick, 5, 5, TimeUnit.SECONDS);
            scheduler.scheduleWithFixedDelay(this::stopIdleServers, IDLE_CHECK_SECONDS, IDLE_CHECK_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * Starts waiting servers and resolves servers that hang in STARTING or STOPPING.
     */
    void tick() {
        for (var server : servers) {
            try {
                switch (server.getLifecycle()) {
                    case WAITING -> server.start();
                    case STARTING -> {
                        if (server.getMillisInLifecycle() > START_TIMEOUT_MILLIS) {
                            Console.warn("%s did not connect %s minutes after its start and is restarted",
                                    server.getName(), START_TIMEOUT_MILLIS / 60_000);
                            stopServer(server);
                        }
                    }
                    case STOPPING -> {
                        if (server.getMillisInLifecycle() > STOP_TIMEOUT_MILLIS) {
                            Console.warn("The stop of %s was not confirmed and is completed", server.getName());
                            finishStop(server);
                        }
                    }
                    default -> {
                    }
                }
            } catch (RuntimeException e) {
                Console.error("Error at %s: %s", server.getName(), e.getMessage());
            }
        }
    }

    /**
     * Stops online servers without players above their group's minimum, unless they are in game.
     */
    private void stopIdleServers() {
        servers.stream().map(AbstractServer::getGroup).distinct().forEach(serverGroup -> {
            var online = getActiveServersFromGroup(serverGroup).stream().filter(AbstractServer::isOnline)
                    .filter(server -> !server.getState().equalsIgnoreCase("ingame")).toList();
            var surplus = online.size() - serverGroup.getMinServers();
            online.stream().filter(server -> server.getOnlinePlayers().isEmpty()).limit(Math.max(0, surplus))
                    .forEach(this::stopServer);
        });
    }

    public boolean startServer(T server) {
        var serverGroup = server.getGroup();
        if (serverGroup.isStaticServer() && getServersAmountFromGroup(serverGroup) >= 1) {
            Console.error("Tried to start a static server that is already running!");
            return false;
        }
        servers.add(server);
        server.start();
        return true;
    }

    public int getServerPort(ServerGroup serverGroup) {
        if (serverGroup.getType().equals(ServerType.PROXY)) {
            return getNextPort(25565);
        }
        return getNextPort(25700);
    }

    /**
     * Stops the server for good, even if its group's minimum then needs a new one.
     */
    public void stopServerAndRemove(T server) {
        server.setRestartAfterStop(false);
        stop(server);
    }

    /**
     * Stops the server. If its group needs it for the minimum, it is started again once it stopped.
     */
    public void stopServer(T server) {
        var group = server.getGroup();
        var othersActive = getActiveServersFromGroup(group).stream().filter(other -> other != server).count();
        server.setRestartAfterStop(groupExists.test(group) && othersActive < group.getMinServers());
        stop(server);
    }

    private void stop(T server) {
        if (server.getLifecycle() == ServerLifecycle.WAITING) {
            // Never started, nothing to stop: it keeps waiting if it is needed, otherwise it is removed
            if (!server.isRestartAfterStop()) {
                server.moveTo(ServerLifecycle.STOPPED);
                servers.remove(server);
            }
            return;
        }
        server.stop();
    }

    /**
     * Completes a stop once the server's process is gone: the server waits to be started again, or is removed.
     */
    public void finishStop(T server) {
        if (server.getLifecycle() != ServerLifecycle.STOPPING) {
            return;
        }
        if (server.isRestartAfterStop() && groupExists.test(server.getGroup())) {
            server.moveTo(ServerLifecycle.WAITING);
        } else {
            server.moveTo(ServerLifecycle.STOPPED);
            servers.remove(server);
        }
    }

    public CompletableFuture<Void> stopAllServers() {
        var futures = new ArrayList<CompletableFuture<AbstractServer>>();
        for (var server : servers) {
            server.setRestartAfterStop(false);
            futures.add(server.stop());
        }
        servers.clear();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Stops all servers of a group for good, e.g. because the group is deleted.
     */
    public void stopServersFromGroup(ServerGroup serverGroup) {
        getServersFromGroup(serverGroup).forEach(this::stopServerAndRemove);
    }

    /**
     * All servers of the group, including stopping ones.
     */
    public List<T> getServersFromGroup(ServerGroup serverGroup) {
        return servers.stream().filter(server -> server.getGroup().equals(serverGroup)).toList();
    }

    /**
     * Servers of the group that are waiting, starting or online, i.e. that count towards its minimum.
     */
    public List<T> getActiveServersFromGroup(ServerGroup serverGroup) {
        return servers.stream().filter(server -> server.getGroup().equals(serverGroup))
                .filter(server -> server.getLifecycle() == ServerLifecycle.WAITING || server.getLifecycle().isActive())
                .toList();
    }

    /**
     * The server with this name; while an earlier run is still stopping, the current one.
     */
    @NotNull
    public Optional<T> getServerByName(String name) {
        return servers.stream().filter(server -> server.getName().equals(name))
                .min(Comparator.comparing(server -> server.getLifecycle() == ServerLifecycle.STOPPING));
    }

    /**
     * Exactly the run of a server with this key, also while it is stopping.
     */
    @NotNull
    public Optional<T> getServerByNameAndKey(String name, UUID key) {
        return servers.stream().filter(server -> server.getName().equals(name) && key != null && key.equals(server.getKey()))
                .findFirst();
    }

    @NotNull
    public Optional<T> getOnlineServerByName(String name) {
        return servers.stream().filter(AbstractServer::isOnline).filter(server -> server.getName().equals(name)).findFirst();
    }

    @NotNull
    public Optional<T> getStartedNotOnlineServer(String name) {
        return servers.stream().filter(AbstractServer::isStarting).filter(server -> server.getName().equals(name)).findFirst();
    }

    @NotNull
    public List<T> getServersByType(ServerType type) {
        return servers.stream().filter(server -> server.getGroup().getType() == type).toList();
    }

    @NotNull
    public Optional<T> getServerByApiServer(Server server) {
        return getServerByName(server.getName());
    }

    public void sendPacketToAllServers(PacketContainer container) {
        servers.stream().filter(AbstractServer::isOnline)
                .filter(server -> server.getChannel() != null && server.getChannel().isActive())
                .forEach(server -> server.getChannel().writeAndFlush(container));
    }

    public void sendPacketToAllServers(ServerType serverType, PacketContainer container) {
        servers.stream().filter(AbstractServer::isOnline)
                .filter(server -> server.getChannel() != null && server.getChannel().isOpen())
                .filter(server -> server.getGroup().getType().equals(serverType))
                .forEach(server -> server.getChannel().writeAndFlush(container));
    }

    /**
     * Servers of the group that are waiting, starting or online.
     */
    public long getServersAmountFromGroup(ServerGroup serverGroup) {
        return getActiveServersFromGroup(serverGroup).size();
    }

    public String getServerNameForNextServer(ServerGroup serverGroup) {
        if (serverGroup.isStaticServer()) {
            return serverGroup.getName();
        }
        var index = 1;
        while (existServerWithName(serverGroup.getName() + "-" + index)) {
            index++;
        }
        return serverGroup.getName() + "-" + index;
    }

    /**
     * Also counts servers that are still stopping, so a new server never gets the name of one that is shutting down.
     */
    public boolean existServerWithName(String name) {
        return servers.stream().anyMatch(server -> server.getName().equals(name));
    }

    /**
     * How long a port that was taken on a wrapper is not given out again.
     */
    private static final long PORT_BLOCK_MILLIS = 10 * 60_000;
    /**
     * Ports something else used on a wrapper, with the time until which servers do not get them.
     */
    private final Map<Integer, Long> blockedPorts = new ConcurrentHashMap<>();

    public void blockPort(int port) {
        blockedPorts.put(port, System.currentTimeMillis() + PORT_BLOCK_MILLIS);
    }

    /**
     * The first free port from the start, skipping ports of servers that are still stopping.
     */
    private int getNextPort(int startIndex) {
        blockedPorts.values().removeIf(until -> until < System.currentTimeMillis());
        var used = new HashSet<Integer>(blockedPorts.keySet());
        servers.forEach(server -> used.add(server.getPort()));
        var port = startIndex;
        while (used.contains(port)) {
            port++;
        }
        return port;
    }

}
