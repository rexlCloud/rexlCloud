package de.rexlcloud.core.server;

import de.rexlcloud.core.api.Player;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import io.netty.channel.Channel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A server or proxy managed by the cloud. Instances are compared by identity: key and port change over time.
 */
@Getter
public abstract class AbstractServer {

    private final String name;
    private final ServerGroup group;
    /**
     * Identifies one run of the server; the master gives every start a new key, so messages about an earlier run
     * (e.g. a late "stopped") cannot affect the current one.
     */
    @Setter(AccessLevel.PROTECTED)
    private volatile UUID key;
    /**
     * Changes if the port turns out to be taken on the wrapper.
     */
    @Setter
    private int port;
    @Setter
    private Channel channel;
    /**
     * Game state reported by the server's plugin, e.g. "ingame".
     */
    @Setter
    @NonNull
    private String state = "";
    private volatile ServerLifecycle lifecycle = ServerLifecycle.WAITING;
    /**
     * When the lifecycle last changed, to notice servers that hang in a state.
     */
    private volatile long lifecycleSince = System.currentTimeMillis();
    /**
     * Whether the server waits to be started again after it stopped, to keep its group's minimum.
     */
    @Setter
    private volatile boolean restartAfterStop;
    protected final ArrayList<Player> onlinePlayers;

    public AbstractServer(String name, ServerGroup group, int port, UUID key) {
        onlinePlayers = new ArrayList<>();
        this.name = name;
        this.group = group;
        this.port = port;
        this.key = key;
    }

    public void addOnlinePlayer(Player player) {
        if (!onlinePlayers.contains(player)) {
            onlinePlayers.add(player);
        }
    }

    public void removeOnlinePlayer(Player player) {
        new ArrayList<>(onlinePlayers)
                .stream()
                .filter(player1 -> player1.getUuid().equals(player.getUuid()))
                .findAny()
                .ifPresent(onlinePlayers::remove);
    }

    public abstract void start();

    public abstract CompletableFuture<AbstractServer> stop();

    public void sendPacket(PacketContainer packet) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(packet);
        }
    }

    /**
     * Moves the server to the next stage of its life, if that transition is allowed.
     *
     * @return whether the server changed; {@code false} if the transition is not allowed (e.g. starting a server
     * that is stopping), so callers can ignore duplicate or late requests
     */
    public boolean moveTo(ServerLifecycle next) {
        ServerLifecycle previous;
        synchronized (this) {
            previous = lifecycle;
            if (!previous.canBecome(next)) {
                Console.debug("%s: %s -> %s ignored", name, previous, next);
                return false;
            }
            lifecycle = next;
            lifecycleSince = System.currentTimeMillis();
        }
        Console.debug("%s: %s -> %s", name, previous, next);
        onLifecycleChange(previous, next);
        return true;
    }

    /**
     * Called after every change of the lifecycle.
     */
    protected void onLifecycleChange(ServerLifecycle previous, ServerLifecycle next) {
    }

    /**
     * Start sent or running: STARTING or ONLINE.
     */
    public boolean isStarted() {
        return lifecycle.isActive();
    }

    public boolean isStarting() {
        return lifecycle == ServerLifecycle.STARTING;
    }

    public boolean isOnline() {
        return lifecycle == ServerLifecycle.ONLINE;
    }

    /**
     * Milliseconds since the lifecycle last changed.
     */
    public long getMillisInLifecycle() {
        return System.currentTimeMillis() - lifecycleSince;
    }
}
