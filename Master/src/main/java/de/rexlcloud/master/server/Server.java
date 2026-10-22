package de.rexlcloud.master.server;

import java.io.File;
import de.rexlcloud.core.api.events.events.server.ServerStartEvent;
import de.rexlcloud.core.api.events.events.server.ServerStopEvent;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.console.LogList;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ProxyRemoveServerPacket;
import de.rexlcloud.core.network.packets.ServerStartPacket;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.core.network.packets.api.ApiUpdateObjectPacket;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.core.server.ServerLifecycle;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.ServerManager;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.wrapper.Wrapper;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Server extends AbstractServer {

    @Nullable
    @Getter
    @Setter
    private Wrapper wrapper = null;
    private long lastMissingJarWarning;
    @Getter
    private LogList logList;

    public Server(String name, ServerGroup group) {
        super(name, group, RexlCloudMaster.getInstance().getServerManager().getServerPort(group), UUID.randomUUID());
        logList = new LogList(2500);
    }

    /**
     * A server that is already running, e.g. one a wrapper kept while the master restarted.
     */
    Server(String name, ServerGroup group, int port, UUID key) {
        super(name, group, port, key);
        logList = new LogList(2500);
    }

    @Override
    public void start() {
        if (!moveTo(ServerLifecycle.STARTING)) {
            return;
        }
        if (isMissingCustomJar() || RexlCloudMaster.getInstance().isWaitingForWrappers()) {
            moveTo(ServerLifecycle.WAITING);
            return;
        }
        if (getGroup().isStaticServer()) {
            wrapper = RexlCloudMaster.getInstance().getWrapperManager().getWrapperForStaticServer(this);
        } else {
            wrapper = RexlCloudMaster.getInstance().getWrapperManager().getWrapperForServer(this);
        }
        if (wrapper == null || wrapper.isInShutdown() || wrapper.getChannel() == null || !wrapper.getChannel().isActive()) {
            // No wrapper can take it right now; the server manager tries again in a few seconds
            moveTo(ServerLifecycle.WAITING);
            return;
        }
        // A new key per run, so messages about an earlier run cannot affect this one
        setKey(UUID.randomUUID());
        var masterConfig = RexlCloudMaster.getInstance().getMasterConfig();
        var serverStartPacket = new ServerStartPacket(getName(), getGroup(), getPort(), getKey(),
                masterConfig.maintenance(), masterConfig.forwarding());
        wrapper.getChannel().writeAndFlush(new PacketContainer(PacketType.SERVER_START, serverStartPacket));
    }

    /**
     * Groups with a custom jar have none until the user puts it into the template; their servers wait for it.
     */
    private boolean isMissingCustomJar() {
        var group = getGroup();
        if (!group.isCustomJar() || group.isStaticServer()
                || new File(group.getDefaultTemplateFolder(), "server.jar").isFile()) {
            return false;
        }
        var now = System.currentTimeMillis();
        if (now - lastMissingJarWarning > 10 * 60_000) {
            lastMissingJarWarning = now;
            Console.warn("%s waits for its server.jar: put it into %s", getName(),
                    group.getDefaultTemplateFolder().getPath());
        }
        return true;
    }

    /**
     * Asks the wrapper to stop the server. The stop is complete when the wrapper reports the process gone (see
     * {@link de.rexlcloud.core.server.ServerManager#finishStop}); without a connected wrapper it is complete at once.
     */
    @Override
    public CompletableFuture<AbstractServer> stop() {
        if (!moveTo(ServerLifecycle.STOPPING)) {
            return CompletableFuture.completedFuture(this);
        }
        setChannel(null);
        setState("");
        var channel = wrapper == null ? null : wrapper.getChannel();
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new PacketContainer(PacketType.SERVER_STOP, new ServerStopPacket(getName(), getKey())));
            if (getGroup().getType() == ServerType.SERVER) {
                removeFromProxies();
            }
        } else {
            RexlCloudMaster.getInstance().getServerManager().finishStop(this);
        }
        onlinePlayers.clear();
        sendApiRemove();
        return CompletableFuture.completedFuture(this);
    }

    /**
     * Tells the proxies to stop sending players to this server.
     */
    public void removeFromProxies() {
        RexlCloudMaster.getInstance().getServerManager().getServers().stream()
                .filter(otherServer -> otherServer.getGroup().getType() == ServerType.PROXY)
                .filter(AbstractServer::isOnline)
                .forEach(otherServer -> {
                    var packet = new PacketContainer(PacketType.PROXY_REMOVE_SERVER, new ProxyRemoveServerPacket(getName()));
                    otherServer.getWrapper().getChannel().writeAndFlush(packet);
                    Console.debug("REMOVE_SERVER %s FROM %s", getName(), otherServer.getName());
                });
    }

    @Override
    protected void onLifecycleChange(ServerLifecycle previous, ServerLifecycle next) {
        if (next == ServerLifecycle.ONLINE && wrapper != null) {
            RexlCloudMaster.getInstance().getEventManager().callEvent(
                    new ServerStartEvent(new de.rexlcloud.core.api.Server(this, wrapper.getApiWrapper())));
            sendApiUpdateToOwnServer();
        } else if (previous == ServerLifecycle.ONLINE && wrapper != null) {
            RexlCloudMaster.getInstance().getEventManager().callEvent(
                    new ServerStopEvent(new de.rexlcloud.core.api.Server(this, wrapper.getApiWrapper())));
        }
    }

    @Override
    public void sendPacket(PacketContainer packetContainer) {
        if (wrapper != null && isOnline()) {
            var channel = getWrapper().getChannel();
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(packetContainer);
            }
        }
    }

    public void sendApiUpdate() {
        if (!isOnline()) return;
        var serverObject = new de.rexlcloud.core.api.Server(this, getWrapper().getApiWrapper());
        RexlCloudMaster.getInstance().getServerManager()
                .getServers().stream().filter(AbstractServer::isStarted).forEach(serverOther -> {
                    var apiUpdateObjectPacket = new ApiUpdateObjectPacket(serverOther.getName(), getName(), serverObject);
                    var packetContainerUpdateObjects = new PacketContainer(PacketType.API_UPDATE_SERVER_OBJECT, apiUpdateObjectPacket);
                    if (serverOther.getWrapper() != null && serverOther.getWrapper().getChannel() != null) {
                        Console.debug("SENDING SERVER_OBJECT_CHANGE SERVER: %s TO: %s", getName(), serverOther.getName());
                        serverOther.getWrapper().getChannel().writeAndFlush(packetContainerUpdateObjects);
                    }
                });
    }

    private void sendApiUpdateToOwnServer() {
        if (!isOnline()) return;
        RexlCloudMaster.getInstance().getServerManager()
                .getServers().stream().filter(server -> server != this)
                .filter(AbstractServer::isStarted).forEach(serverOther -> {
                    var serverObject = new de.rexlcloud.core.api.Server(serverOther, getWrapper().getApiWrapper());
                    var apiUpdateObjectPacket = new ApiUpdateObjectPacket(getName(), serverOther.getName(), serverObject);
                    var packetContainerUpdateObjects = new PacketContainer(PacketType.API_UPDATE_SERVER_OBJECT, apiUpdateObjectPacket);
                    if (getWrapper() != null && getWrapper().getChannel() != null) {
                        Console.debug("SENDING SERVER_OBJECT_CHANGE SERVER: %s TO: %s", serverOther.getName(), getName());
                        getWrapper().getChannel().writeAndFlush(packetContainerUpdateObjects);
                    }
                });
    }

    private void sendApiRemove() {
        ServerManager<Server> serverManager = RexlCloudMaster.getInstance().getServerManager();
        serverManager.getServers().stream().filter(AbstractServer::isStarted).filter(server -> server != this).forEach(serverOther -> {
            var apiUpdateObjectPacket = new ApiUpdateObjectPacket(serverOther.getName(), getName(), null);
            var packetContainerUpdateObjects = new PacketContainer(PacketType.API_UPDATE_SERVER_OBJECT, apiUpdateObjectPacket);
            if (serverOther.getWrapper() != null && serverOther.getWrapper().getChannel() != null) {
                Console.debug("SENDING SERVER_OBJECT_CHANGE with null server: %s TO: %s", getName(), serverOther.getName());
                serverOther.getWrapper().getChannel().writeAndFlush(packetContainerUpdateObjects);
            }
        });
    }

}
