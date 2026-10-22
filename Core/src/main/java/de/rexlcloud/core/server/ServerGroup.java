package de.rexlcloud.core.server;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;

/**
 * A group of servers or proxies started from the same template with the same settings, e.g. {@code Lobby}.
 * Changing a group is done by the master (module API or panel); the setters only change this copy.
 */
@Getter
public class ServerGroup implements Serializable {

    /**
     * Name of the group; its servers are called {@code <name>-1}, {@code <name>-2}, ...
     */
    @NonNull
    private String name;
    /**
     * Servers or proxies.
     */
    @NonNull
    private ServerType type;
    /**
     * Memory of each server in MB.
     */
    @Setter
    private int memory;
    /**
     * Whether the group has one server that keeps its files on a fixed wrapper instead of starting fresh from the
     * template.
     */
    private boolean staticServer;
    /**
     * The wrapper of a static group, {@code null} otherwise.
     */
    @Nullable
    private String wrapper;
    /**
     * How many servers the cloud keeps running.
     */
    @Setter
    private int minServers;
    /**
     * Whether players are sent to this group's servers when they join or are kicked from another server.
     */
    @Setter
    private boolean fallback;
    /**
     * Whether the logs of a static server are deleted when it starts.
     */
    @Setter
    private boolean removeOldLogs;
    /**
     * Extra JVM arguments of the servers, e.g. {@code -XX:+UseG1GC}.
     */
    @Setter
    @NonNull
    private String javaArguments;
    /**
     * Player slots shown in the server list.
     */
    @Setter
    private int maxPlayers;
    /**
     * The Java the servers run with: {@code java} from the wrapper's PATH or the path of an installed runtime.
     */
    @Setter
    private String javaCommand;
    /**
     * The group's servers run the {@code server.jar} the user put into the template (e.g. a Minestom server with the
     * MinestomBridge) instead of a downloaded Paper/Spigot jar. Such servers get no cloud plugin, no Paper/Spigot
     * configuration and no start arguments; they read everything from their cloud.json.
     */
    @Setter
    private boolean customJar;

    /**
     * Internal: groups are created by the master.
     */
    public ServerGroup(@NonNull String name, @NonNull ServerType type, int memory, boolean staticServer,
                       @Nullable String wrapper, int minServers, boolean fallback, boolean removeOldLogs,
                       @NonNull String javaArguments, int maxPlayers, String javaCommand) {
        this.name = name;
        this.type = type;
        this.memory = memory;
        this.staticServer = staticServer;
        this.wrapper = wrapper;
        this.minServers = minServers;
        this.fallback = fallback;
        this.removeOldLogs = removeOldLogs;
        this.javaArguments = javaArguments;
        this.maxPlayers = maxPlayers;
        this.javaCommand = javaCommand;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ServerGroup that = (ServerGroup) o;
        return memory == that.memory && staticServer == that.staticServer && minServers == that.minServers
                && fallback == that.fallback && removeOldLogs == that.removeOldLogs && name.equals(that.name)
                && type == that.type;
    }

    public File getTemplateFolder() {
        return new File("templates", name);
    }

    /**
     * Template every server of the group starts from. It lives inside {@link #getTemplateFolder()}, because only
     * that folder is sent to the wrappers, which expect it as {@code servers/templates/<group>/default}.
     */
    public File getDefaultTemplateFolder() {
        return new File(getTemplateFolder(), "default");
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, memory, staticServer, wrapper, minServers, fallback);
    }
}
