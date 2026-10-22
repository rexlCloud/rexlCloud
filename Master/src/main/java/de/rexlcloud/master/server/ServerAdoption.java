package de.rexlcloud.master.server;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerSyncRequestPacket;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.core.network.packets.WrapperConnectPacket.RunningServer;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.core.server.ServerLifecycle;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.wrapper.Wrapper;
import org.jetbrains.annotations.Nullable;

import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.ServerManager;

import java.util.HashSet;
import java.util.Optional;
import java.util.function.Function;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servers keep running on their wrapper while the master restarts or the connection breaks. When the wrapper
 * connects, it reports them and the master continues with them instead of starting new ones.
 */
public final class ServerAdoption {

    private ServerAdoption() {
    }

    public static void adopt(Wrapper wrapper, @Nullable List<RunningServer> running) {
        var master = RexlCloudMaster.getInstance();
        var adopted = adopt(wrapper, running, master.getServerManager(), master.getServerGroupManager()::getServerGroupByName);
        if (adopted > 0) {
            Console.info("Took over %s server(s) that kept running on %s", adopted, wrapper.getName());
            // The proxies report their players again once they are online on the master
            CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(ServerAdoption::requestPlayers);
        }
    }

    /**
     * @param groups the group of a name, if it exists
     * @return how many servers were taken over
     */
    static int adopt(Wrapper wrapper, @Nullable List<RunningServer> running, ServerManager<Server> serverManager,
                     Function<String, Optional<ServerGroup>> groups) {
        var reported = running == null ? List.<RunningServer>of() : running;

        // The master kept running and only lost the connection: its servers move to the new connection, and what
        // the wrapper no longer has is gone
        var oldConnections = new HashSet<Wrapper>();
        for (var server : serverManager.getServers()) {
            var old = server.getWrapper();
            if (old == null || old == wrapper || !old.getName().equals(wrapper.getName())) {
                continue;
            }
            oldConnections.add(old);
            if (reported.stream().anyMatch(r -> r.getName().equals(server.getName()) && r.getKey().equals(server.getKey()))) {
                server.setWrapper(wrapper);
            }
        }
        oldConnections.forEach(old -> old.releaseServers(serverManager));

        // The master restarted: it takes over what it does not know
        var adopted = 0;
        for (var runningServer : reported) {
            if (serverManager.getServerByNameAndKey(runningServer.getName(), runningServer.getKey()).isPresent()) {
                continue;
            }
            var group = groups.apply(runningServer.getGroup()).orElse(null);
            if (group == null) {
                stopOnWrapper(wrapper, runningServer, "its group no longer exists");
                continue;
            }
            var sameName = serverManager.getServers().stream()
                    .filter(server -> server.getName().equals(runningServer.getName())).findFirst();
            if (sameName.isPresent() && sameName.get().getLifecycle() != ServerLifecycle.WAITING) {
                stopOnWrapper(wrapper, runningServer, "a server of that name already runs elsewhere");
                continue;
            }
            // It takes the place of a server that waits to be started for the group's minimum
            sameName.or(() -> serverManager.getServersFromGroup(group).stream()
                            .filter(server -> server.getLifecycle() == ServerLifecycle.WAITING).findFirst())
                    .ifPresent(waiting -> {
                        waiting.moveTo(ServerLifecycle.STOPPED);
                        serverManager.getServers().remove(waiting);
                    });
            var server = new Server(runningServer.getName(), group, runningServer.getPort(), runningServer.getKey());
            server.setWrapper(wrapper);
            serverManager.getServers().add(server);
            // ONLINE follows with the wrapper's server info
            server.moveTo(ServerLifecycle.STARTING);
            adopted++;
        }
        return adopted;
    }

    /**
     * Asks every online proxy to report its players; players the master already knows are skipped.
     */
    static void requestPlayers() {
        RexlCloudMaster.getInstance().getServerManager().getServers().stream()
                .filter(server -> server.getGroup().getType() == ServerType.PROXY)
                .filter(AbstractServer::isOnline)
                .filter(proxy -> proxy.getWrapper() != null)
                .forEach(proxy -> proxy.getWrapper().sendPacket(new PacketContainer(PacketType.PLAYER_SYNC_REQUEST,
                        new PlayerSyncRequestPacket(proxy.getName()))));
    }

    private static void stopOnWrapper(Wrapper wrapper, RunningServer server, String reason) {
        Console.warn("Stopping %s on %s, %s", server.getName(), wrapper.getName(), reason);
        wrapper.sendPacket(new PacketContainer(PacketType.SERVER_STOP, new ServerStopPacket(server.getName(), server.getKey())));
    }

}
