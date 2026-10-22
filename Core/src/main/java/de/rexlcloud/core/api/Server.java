package de.rexlcloud.core.api;

import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.AbstractServer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A server or proxy of the network as the API sees it. The object is updated by the cloud while the server
 * runs; servers that stopped are removed from {@link RexlCloudApi}'s cache.
 */
@Getter
@AllArgsConstructor
public class Server implements Serializable {
    /**
     * Name of the server, e.g. {@code Lobby-1}.
     */
    @NonNull
    private String name;
    /**
     * Identifies this run of the server; a new run (after a restart) gets a new key.
     */
    @NonNull
    private UUID key;
    /**
     * The group the server belongs to.
     */
    @NonNull
    private ServerGroup serverGroup;
    /**
     * The port players (or the proxy) connect to.
     */
    private int port;
    /**
     * The wrapper the server runs on.
     */
    @NonNull
    private Wrapper wrapper;
    /**
     * A free-form state the server set with {@link #setState(String)}, e.g. {@code "ingame"}; empty by default.
     * Servers in the state {@code ingame} are never stopped for being idle.
     */
    @NonNull
    private String state;
    /**
     * Whether the server is online: started and its cloud plugin is connected.
     */
    private boolean online;

    /**
     * Internal: servers come from {@link RexlCloudApi}.
     */
    public Server(@NonNull AbstractServer server, @NonNull Wrapper wrapper) {
        this.name = server.getName();
        this.key = server.getKey();
        this.serverGroup = server.getGroup();
        this.port = server.getPort();
        this.wrapper = wrapper;
        this.state = server.getState();
        this.online = server.isOnline();
    }

    /**
     * Internal: applies a newer state of the server.
     */
    protected void update(@NonNull Server server, @NonNull Wrapper wrapper) {
        this.name = server.getName();
        this.key = server.getKey();
        this.serverGroup = server.getServerGroup();
        this.port = server.getPort();
        this.wrapper = wrapper;
        this.state = server.getState();
        this.online = server.isOnline();
    }

    /**
     * Internal: replaces the wrapper with the cached object.
     */
    protected void updateWrapper(@NonNull Wrapper wrapper) {
        this.wrapper = wrapper;
    }

    /**
     * Sets the state of the server this plugin runs on, e.g. {@code "ingame"} for a running round; other servers
     * and the web panel see it. Only possible for the own server: called on another server's object it completes
     * exceptionally with an {@link IllegalStateException}.
     *
     * @param state the new state, empty for none
     * @return completes once the master received it
     */
    public CompletableFuture<Boolean> setState(String state) {
        var api = RexlCloudApi.getInstance();
        if (!name.equals(api.getServerName())) {
            // The request always changes the server it comes from
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Only the server itself can set its state, not " + name + " from " + api.getServerName()));
        }
        return api.createRequest(PacketType.API_SERVER_STATE_CHANGE, state);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var server = (Server) o;
        return port == server.port && Objects.equals(name, server.name) && Objects.equals(key, server.key) && Objects.equals(serverGroup, server.serverGroup) && Objects.equals(wrapper, server.wrapper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, key, serverGroup, port, wrapper);
    }
}
