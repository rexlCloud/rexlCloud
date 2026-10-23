package de.rexlcloud.core.api.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What a module can do with the cloud; get it with {@link CloudModule#getModuleApi()}.
 * <p>
 * Methods that change something return a {@link ModuleApiResult}; queries return JSON with snake_case keys, the
 * same as the REST API of the RestModule.
 */
public interface ModuleApi {

    /**
     * @param limit how many entries, newest first
     * @return the network's statistics, one entry per minute ({@code time}, {@code cpu_usage}, {@code used_memory},
     *         {@code free_memory}, {@code online_servers}, {@code started_servers}, {@code online_players})
     */
    JsonArray getServerStats(int limit);
    /**
     * Runs a console command on a server.
     *
     * @return {@code false} if the server does not exist or is not online
     */
    boolean execute(String serverName, String command);
    /**
     * Stops a server; its group starts a new one if it needs it for its minimum.
     *
     * @return {@code false} if the server does not exist
     */
    boolean stop(String serverName);
    /**
     * Starts more servers of a group.
     *
     * @return {@code false} if the group does not exist
     */
    boolean start(String serverGroup, int amount);

    /**
     * @param serverName a server, {@code null} for all
     * @return the servers with name, port, state, group, wrapper, players and {@code online_state} ({@code waiting},
     *         {@code started}, {@code online} or {@code stopping})
     */
    JsonArray getServerInfos(@Nullable String serverName);
    /**
     * @param serverName a group, {@code null} for all
     * @return the groups with their settings and servers
     */
    JsonArray getServerGroupsInfos(@Nullable String serverName);

    /**
     * @return the last console lines of the server, empty if it does not exist
     */
    String[] getServerConsole(@NonNull String serverName);

    /**
     * Counts of servers, players, wrappers and groups plus the maintenance state.
     */
    JsonObject getOverview();

    // Servers

    /**
     * Forcefully kills the server process on its wrapper.
     */
    ModuleApiResult kill(@NonNull String serverName);

    /**
     * Stops the server and starts a new one from the same group.
     */
    ModuleApiResult restart(@NonNull String serverName);

    /**
     * Saves the files of a running server into a template variant of its group (e.g. "default" or a map), replacing
     * files with the same path. Logs, caches, the server jar and the cloud plugin are left out.
     *
     * @return OK with the number of files once saved, ACCEPTED if it takes longer than a minute
     */
    ModuleApiResult saveServerToTemplate(@NonNull String serverName, @NonNull String variant);

    // Template files (the master's templates/ folder; changes reach the wrappers through the template sync)

    /**
     * Templates that can be edited: every non-static group with its variants, and global/server and global/proxy.
     */
    JsonArray getTemplates();

    /**
     * Entries of a folder in a template ({@code path} empty for the template itself); data is the array.
     */
    ModuleApiResult listTemplateFiles(@NonNull String group, @NonNull String variant, @Nullable String path);

    /**
     * Data is the absolute path of the file, for reading it.
     */
    ModuleApiResult templateFile(@NonNull String group, @NonNull String variant, @NonNull String path);

    /**
     * Creates or replaces a file with the content (at most 1 GB).
     */
    ModuleApiResult writeTemplateFile(@NonNull String group, @NonNull String variant, @NonNull String path,
                                      @NonNull java.io.InputStream content);

    /**
     * Creates a folder in a template ({@code "."} creates the variant itself).
     */
    ModuleApiResult createTemplateFolder(@NonNull String group, @NonNull String variant, @NonNull String path);

    /**
     * Deletes a file or folder; with an empty path a whole variant (not "default").
     */
    ModuleApiResult deleteTemplateFile(@NonNull String group, @NonNull String variant, @Nullable String path);

    /**
     * Calls the listener for every new console line of the server until the returned handle is closed.
     *
     * @return empty if the server does not exist
     */
    Optional<AutoCloseable> subscribeConsole(@NonNull String serverName, @NonNull Consumer<String> listener);

    // Server groups

    /**
     * Server jars that can be installed into a new group.
     */
    JsonArray getServerJars();

    /**
     * Creates a group, installs the server jar and starts its servers in the background.
     *
     * @param wrapper    wrapper for static groups, ignored otherwise
     * @param minServers servers to keep online, ignored for static groups
     * @param serverJar  full text of the server jar as listed by {@link #getServerJars()}
     */
    ModuleApiResult createServerGroup(@NonNull String name, @NonNull String type, int memory, boolean staticServer,
                                      @Nullable String wrapper, int minServers, boolean fallback,
                                      @NonNull String serverJar);

    /**
     * Changes the given values of a group; {@code null} values are left unchanged.
     */
    ModuleApiResult editServerGroup(@NonNull String name, @Nullable Integer memory, @Nullable Integer maxPlayers,
                                    @Nullable Integer minServers, @Nullable Boolean fallback,
                                    @Nullable Boolean removeOldLogs);

    /**
     * Java installations reported by the connected wrappers, one entry per executable path with
     * {@code path}, {@code version}, {@code major}, {@code vendor}, {@code name} and the {@code wrappers} having it.
     */
    JsonArray getJavaRuntimes();

    /**
     * Sets the Java runtime the servers of a group run with: {@code "java"} for the one on the wrapper's PATH, or
     * the path of a runtime listed by {@link #getJavaRuntimes()}. New servers use it; running ones keep theirs.
     */
    ModuleApiResult setServerGroupJavaRuntime(@NonNull String name, @NonNull String javaCommand);

    // Server list

    /**
     * The MOTD the proxies show, as MiniMessage: {@code motd}, {@code maintenance_motd} (shown during maintenance)
     * and {@code maintenance}.
     */
    JsonObject getMotd();

    /**
     * Changes the MOTD of all proxies; {@code null} values stay unchanged. Takes effect with the next server list
     * ping, without restarting the proxies.
     */
    ModuleApiResult setMotd(@Nullable String motd, @Nullable String maintenanceMotd);

    /**
     * Renders MiniMessage (and {@code &} colour codes) to a JSON chat component, exactly as players see it.
     */
    String renderText(@NonNull String text);

    /**
     * Deletes a group and stops its servers for good.
     */
    ModuleApiResult deleteServerGroup(@NonNull String name);

    /**
     * Makes all wrappers check the group's template again and repair differences.
     */
    ModuleApiResult syncServerGroupTemplate(@NonNull String name);

    // Players

    /**
     * @return the online players with name, UUID, server, proxy and login time
     */
    JsonArray getPlayers();

    /**
     * Disconnects a player from the network.
     *
     * @param message reason (MiniMessage), {@code null} for none
     */
    ModuleApiResult kickPlayer(@NonNull UUID uuid, @Nullable String message);

    /**
     * Sends a chat message (MiniMessage) to a player.
     */
    ModuleApiResult messagePlayer(@NonNull UUID uuid, @NonNull String message);

    /**
     * A player who joined the network at least once, online or not, with the data plugins stored for them.
     *
     * @param player UUID or name (case-insensitive) of the player
     * @return data: {@code uuid}, {@code name}, {@code first_login}, {@code last_login}, {@code online} (with
     *         {@code server}, {@code server_group}, {@code proxy} and {@code login} while online) and {@code data}:
     *         the stored values, see {@link de.rexlcloud.core.api.PlayerDataValues#describe}; not found if the
     *         player never joined
     */
    ModuleApiResult getPlayer(@NonNull String player);

    /**
     * Stores a value for a player, whether they are online or not, as plugins do with {@code setData}.
     *
     * @param player UUID or name of a player who joined at least once
     * @param key    name of the value
     * @param type   one of {@link de.rexlcloud.core.api.PlayerDataValues#WRITABLE_TYPES}; {@code null} keeps the
     *               type of the current value, or picks one from the JSON value for a new key
     * @param value  a JSON string, number or boolean
     * @return data: the stored value, as in {@link #getPlayer(String)}
     */
    ModuleApiResult setPlayerData(@NonNull String player, @NonNull String key, @Nullable String type,
                                  @NonNull JsonElement value);

    /**
     * Removes a value stored for a player.
     *
     * @param player UUID or name of a player who joined at least once
     * @return not found if the player never joined or has no value under this key
     */
    ModuleApiResult removePlayerData(@NonNull String player, @NonNull String key);

    // Wrappers

    /**
     * Registered wrappers with their connection state, resource usage and servers.
     */
    JsonArray getWrappers();

    /**
     * Registers credentials for a new wrapper. A random key is generated when {@code key} is {@code null}.
     * The result data contains {@code name}, {@code address} and {@code key}.
     */
    ModuleApiResult addWrapper(@NonNull String address, @Nullable UUID key);

    /**
     * Shuts a wrapper down; it stops its servers.
     */
    ModuleApiResult stopWrapper(@NonNull String name);

    // Maintenance

    /**
     * @return whether maintenance mode is on: only players with {@code rexlcloud.maintenance.join} can join
     */
    boolean isMaintenance();

    /**
     * Turns maintenance mode on or off for the whole network.
     */
    void setMaintenance(boolean maintenance);

    // Modules

    /**
     * Loaded modules and the jar files available in the module folder.
     */
    JsonObject getModules();

    /**
     * Loads a module jar from the {@code modules/} folder.
     */
    ModuleApiResult loadModule(@NonNull String fileName);

    /**
     * Reloads a loaded module (calls {@link CloudModule#onReload()}).
     */
    ModuleApiResult reloadModule(@NonNull String name);

    /**
     * Disables a loaded module.
     */
    ModuleApiResult disableModule(@NonNull String name);

}
