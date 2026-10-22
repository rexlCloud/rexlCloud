package de.rexlcloud.master.wrapper;

import de.rexlcloud.master.server.Server;
import de.rexlcloud.core.server.ServerManager;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.WrapperInfoPacket;
import de.rexlcloud.core.network.packets.api.ApiUpdateObjectPacket;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.ServerLifecycle;
import de.rexlcloud.core.server.runtime.JavaRuntime;
import io.netty.channel.Channel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Getter
public class Wrapper {

    private static final long HEARTBEAT_TIMEOUT_MILLIS = 30_000;

    private final Channel channel;
    private final String name;
    private final String address;

    private double cpuUsage;
    private long usedMemory, freeMemory, totalMemory;
    /**
     * Java installations the wrapper reported, empty until its first info packet.
     */
    private volatile List<JavaRuntime> javaRuntimes = List.of();

    @Getter(AccessLevel.PRIVATE)
    private volatile long lastPacketInfo = 0;
    @Getter(AccessLevel.PRIVATE)
    private boolean connected = true;
    @Setter
    private boolean inShutdown = false;

    public Wrapper(Channel channel, String name, String address) {
        this.channel = channel;
        this.name = name;
        this.address = address;
        // Wrappers send their info every five seconds; one that stays silent is frozen or cut off
        lastPacketInfo = System.currentTimeMillis();
        var heartbeat = channel.eventLoop().scheduleAtFixedRate(() -> {
            if (connected && System.currentTimeMillis() - lastPacketInfo > HEARTBEAT_TIMEOUT_MILLIS) {
                Console.error("Wrapper %s has not answered for %s seconds, disconnecting it", name,
                        HEARTBEAT_TIMEOUT_MILLIS / 1000);
                channel.close();
            }
        }, 5, 5, TimeUnit.SECONDS);
        channel.closeFuture().addListener(closed -> heartbeat.cancel(false));
    }

    /**
     * Whether servers of the group can start here: "java" from PATH always, a runtime path only if reported.
     * Wrappers that reported nothing yet are not ruled out.
     */
    public boolean canRun(ServerGroup group) {
        var javaCommand = group.getJavaCommand();
        if (javaCommand == null || javaCommand.isBlank() || javaCommand.equals("java") || javaRuntimes.isEmpty()) {
            return true;
        }
        return javaRuntimes.stream().anyMatch(runtime -> runtime.executable().equals(javaCommand));
    }

    public void handleWrapperInfoPacket(WrapperInfoPacket wrapperInfoPacket) {
        lastPacketInfo = System.currentTimeMillis();
        cpuUsage = wrapperInfoPacket.getCpuUsage();
        usedMemory = wrapperInfoPacket.getUsedMemory();
        freeMemory = wrapperInfoPacket.getFreeMemory();
        totalMemory = wrapperInfoPacket.getTotalMemory();
        if (wrapperInfoPacket.getJavaRuntimes() != null) {
            javaRuntimes = List.copyOf(wrapperInfoPacket.getJavaRuntimes());
        }
        RexlCloudMaster.getInstance().getServerManager().getServers().forEach(server -> {
            var packet = new ApiUpdateObjectPacket(server.getName(), getName(), getApiWrapper());
            var container = new PacketContainer(PacketType.API_UPDATE_WRAPPER_OBJECT, packet);
            server.sendPacket(container);
        });
    }

    public de.rexlcloud.core.api.Wrapper getApiWrapper() {
        return new de.rexlcloud.core.api.Wrapper(name, connected, cpuUsage, usedMemory, freeMemory, totalMemory);
    }

    /**
     * Tells the wrapper to shut down, which stops its servers.
     */
    public void stop() {
        if (connected) {
            var container = new PacketContainer(PacketType.WRAPPER_STOP, null);
            channel.writeAndFlush(container);
            connected = false;
            releaseServers();
        }
    }

    /**
     * The connection is gone; its servers may still run (see {@link WrapperManager#unregisterWrapper}).
     */
    void disconnected() {
        connected = false;
    }

    /**
     * Its servers that still belong to this connection are gone: no stop confirmation will come, so their stops
     * are completed here (and needed ones start again elsewhere).
     */
    public void releaseServers() {
        releaseServers(RexlCloudMaster.getInstance().getServerManager());
    }

    public void releaseServers(ServerManager<Server> serverManager) {
        serverManager.getServers().stream()
                .filter(server -> server.getWrapper() == this)
                .filter(server -> server.isStarted() || server.getLifecycle() == ServerLifecycle.STOPPING)
                .forEach(server -> {
                    if (server.isStarted()) {
                        serverManager.stopServer(server);
                    }
                    serverManager.finishStop(server);
                });
    }

    public void sendPacket(@NonNull PacketContainer container) {
        if (connected) {
            channel.writeAndFlush(container);
        }
    }

    public void sendMessages() {
        var container = new PacketContainer(PacketType.SYNC_MESSAGES, RexlCloudMaster.getInstance().getPluginMessageConfig());
        sendPacket(container);
    }
}
