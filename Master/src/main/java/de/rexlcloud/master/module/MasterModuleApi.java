package de.rexlcloud.master.module;

import java.util.List;
import com.google.gson.JsonPrimitive;
import java.nio.file.Path;
import de.rexlcloud.master.server.TemplateFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.rexlcloud.core.api.module.ModuleApi;
import de.rexlcloud.core.api.module.ModuleApiResult;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.database.helper.QueryFilter;
import de.rexlcloud.core.database.helper.QueryType;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.PlayerKickPacket;
import de.rexlcloud.core.network.packets.PlayerMessagePacket;
import de.rexlcloud.core.network.packets.ServerExecuteCommandPacket;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.core.server.AbstractServer;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.core.api.CloudOfflinePlayer;
import de.rexlcloud.core.api.CloudText;
import de.rexlcloud.core.api.PlayerDataValues;
import de.rexlcloud.core.api.PlayerDatabaseData;
import de.rexlcloud.core.config.MessageKey;
import de.rexlcloud.core.server.files.ServerJar;
import de.rexlcloud.core.server.template.Templates;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.database.CloudStats;
import de.rexlcloud.master.server.Server;
import de.rexlcloud.master.wrapper.Wrapper;
import de.rexlcloud.master.wrapper.WrapperCredentials;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public class MasterModuleApi implements ModuleApi {

    private static final Pattern GROUP_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "module-api-worker");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public JsonArray getServerStats(int limit) {
        var jsonArray = new JsonArray();
        var cloudStats = RexlCloudMaster.getInstance().getDatabaseConnection().getObjectHandler().
                getDataFromClass(CloudStats.class, new QueryFilter(QueryType.LIMIT, "", limit),
                        new QueryFilter(QueryType.ORDER_BY_DESC, "id", null)).join();
        for (var cloudStat : cloudStats) {
            var jsonObject = new JsonObject();
            jsonObject.addProperty("time", cloudStat.getTimestamp().getTime());
            jsonObject.addProperty("cpu_usage", cloudStat.getCpuUsage());
            jsonObject.addProperty("used_memory", cloudStat.getUsedMemory());
            jsonObject.addProperty("free_memory", cloudStat.getFreeMemory());
            jsonObject.addProperty("online_servers", cloudStat.getOnlineServerCount());
            jsonObject.addProperty("started_servers", cloudStat.getStartedServerCount());
            jsonObject.addProperty("online_players", cloudStat.getOnlinePlayerCount());
            jsonArray.add(jsonObject);
        }
        return jsonArray;
    }

    @Override
    public boolean execute(String serverName, String command) {
        var executed = new AtomicBoolean(false);
        RexlCloudMaster.getInstance().getServerManager().getServerByName(serverName).ifPresent(server -> {
            if (command == null) {
                return;
            }
            var serverExecutePacket = new ServerExecuteCommandPacket(server.getName(), command);
            var packetContainer = new PacketContainer(PacketType.SERVER_EXECUTE_COMMAND, serverExecutePacket);
            server.sendPacket(packetContainer);
            executed.set(true);
        });
        return executed.get();
    }

    @Override
    public boolean stop(String serverName) {
        var executed = new AtomicBoolean(false);
        RexlCloudMaster.getInstance().getServerManager().getServerByName(serverName).ifPresent(server -> {
            RexlCloudMaster.getInstance().getServerManager().stopServer(server);
            executed.set(true);
        });
        return executed.get();
    }

    @Override
    public boolean start(String serverGroupName, int amount) {
        if (amount < 1) {
            return false;
        }
        var executed = new AtomicBoolean(false);
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(serverGroupName).ifPresent(serverGroup -> {
            var serverManager = RexlCloudMaster.getInstance().getServerManager();
            if (serverGroup.isStaticServer() && serverManager.getServersAmountFromGroup(serverGroup) >= 1) {
                return;
            }
            for (int i = 0; i < amount; i++) {
                var server = new Server(serverManager.getServerNameForNextServer(serverGroup), serverGroup);
                serverManager.startServer(server);
            }
            executed.set(true);
        });
        return executed.get();
    }

    @Override
    public JsonArray getServerInfos(@Nullable String serverName) {
        var jsonArray = new JsonArray();
        RexlCloudMaster.getInstance().getServerManager().getServers().stream()
                .filter(server -> serverName == null || serverName.isBlank() || server.getName().equals(serverName))
                .forEach(server -> {
                    var jsonObject = new JsonObject();
                    jsonObject.addProperty("server_name", server.getName());
                    jsonObject.addProperty("server_port", server.getPort());
                    jsonObject.addProperty("server_state", server.getState());
                    jsonObject.addProperty("wrapper", server.getWrapper() == null ? null : server.getWrapper().getName());
                    jsonObject.addProperty("server_group", server.getGroup().getName());
                    jsonObject.addProperty("server_type", server.getGroup().getType().name());
                    jsonObject.addProperty("online_players", server.getOnlinePlayers().size());
                    jsonObject.addProperty("max_players", server.getGroup().getMaxPlayers());
                    jsonObject.addProperty("memory", server.getGroup().getMemory());
                    jsonObject.addProperty("static", server.getGroup().isStaticServer());
                    jsonObject.addProperty("online_state", switch (server.getLifecycle()) {
                        case WAITING -> "waiting";
                        case STARTING -> "started";
                        case ONLINE -> "online";
                        case STOPPING, STOPPED -> "stopping";
                    });
                    var players = new JsonArray();
                    new ArrayList<>(server.getOnlinePlayers()).forEach(player -> players.add(player.getName()));
                    jsonObject.add("player_names", players);
                    jsonArray.add(jsonObject);
                });
        return jsonArray;
    }

    @Override
    public JsonArray getServerGroupsInfos(@Nullable String groupName) {
        var jsonArray = new JsonArray();
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroups().stream()
                .filter(group -> groupName == null || groupName.isBlank() || group.getName().equals(groupName))
                .forEach(group -> {
                    var jsonObject = new JsonObject();
                    jsonObject.addProperty("group_name", group.getName());
                    jsonObject.addProperty("group_type", group.getType().name());
                    jsonObject.addProperty("group_memory", group.getMemory());
                    jsonObject.addProperty("group_java_args", group.getJavaArguments());
                    jsonObject.addProperty("group_java_cmd", group.getJavaCommand());
                    jsonObject.addProperty("group_map_players", group.getMaxPlayers());
                    jsonObject.addProperty("group_static", group.isStaticServer());
                    jsonObject.addProperty("group_fallback", group.isFallback());
                    jsonObject.addProperty("group_remove_old_logs", group.isRemoveOldLogs());
                    jsonObject.addProperty("group_wrapper", group.getWrapper());
                    jsonObject.addProperty("group_min_servers", group.getMinServers());
                    var servers = new JsonArray();
                    var onlinePlayers = 0;
                    for (var server : RexlCloudMaster.getInstance().getServerManager().getServersFromGroup(group)) {
                        servers.add(server.getName());
                        onlinePlayers += server.getOnlinePlayers().size();
                    }
                    jsonObject.add("servers", servers);
                    jsonObject.addProperty("online_players", onlinePlayers);
                    jsonArray.add(jsonObject);
                });
        return jsonArray;
    }

    @Override
    public String[] getServerConsole(@NonNull String serverName) {
        var list = new ArrayList<String>();
        try {
            RexlCloudMaster.getInstance().getServerManager().getServerByName(serverName)
                    .ifPresent(server -> list.addAll(server.getLogList().snapshot()));
        } catch (Exception e) {
            Console.error(e.getMessage());
        }
        return list.toArray(String[]::new);
    }

    @Override
    public JsonObject getOverview() {
        var master = RexlCloudMaster.getInstance();
        var servers = master.getServerManager().getServers();
        var jsonObject = new JsonObject();
        jsonObject.addProperty("servers_total", servers.size());
        jsonObject.addProperty("servers_online", servers.stream().filter(AbstractServer::isOnline).count());
        jsonObject.addProperty("proxies_online", servers.stream().filter(AbstractServer::isOnline)
                .filter(server -> server.getGroup().getType() == ServerType.PROXY).count());
        jsonObject.addProperty("players_online", master.getPlayerManager().getPlayers().size());
        jsonObject.addProperty("groups", master.getServerGroupManager().getServerGroups().size());
        jsonObject.addProperty("wrappers_registered", master.getWrapperManager().getWrapperRegistry().size());
        jsonObject.addProperty("wrappers_connected", master.getWrapperManager().getWrappers().size());
        jsonObject.addProperty("maintenance", master.getMasterConfig().maintenance());
        jsonObject.addProperty("version", master.getVersion());
        return jsonObject;
    }

    // Servers

    @Override
    public ModuleApiResult kill(@NonNull String serverName) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        var server = serverManager.getServerByName(serverName).orElse(null);
        if (server == null) {
            return ModuleApiResult.notFound("Server " + serverName + " does not exist");
        }
        var wrapper = server.getWrapper();
        if (wrapper == null || !server.isStarted()) {
            serverManager.stopServer(server);
            return ModuleApiResult.ok();
        }
        // The wrapper reports the stop back to the master, which then cleans up like a normal stop
        wrapper.sendPacket(new PacketContainer(PacketType.SERVER_KILL, new ServerStopPacket(server.getName(), server.getKey())));
        return ModuleApiResult.accepted("Server " + serverName + " is being killed");
    }

    @Override
    public ModuleApiResult restart(@NonNull String serverName) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        var server = serverManager.getServerByName(serverName).orElse(null);
        if (server == null) {
            return ModuleApiResult.notFound("Server " + serverName + " does not exist");
        }
        var group = server.getGroup();
        serverManager.stopServer(server);
        // A server the group needs for its minimum is started again once it stopped; any other gets a replacement
        if (!server.isRestartAfterStop() && !group.isStaticServer()) {
            serverManager.startServer(new Server(serverManager.getServerNameForNextServer(group), group));
        }
        return ModuleApiResult.accepted("Server " + serverName + " is restarting");
    }

    @Override
    public ModuleApiResult saveServerToTemplate(@NonNull String serverName, @NonNull String variant) {
        var master = RexlCloudMaster.getInstance();
        var server = master.getServerManager().getServerByName(serverName).orElse(null);
        if (server == null) {
            return ModuleApiResult.notFound("Server " + serverName + " does not exist");
        }
        var result = master.getTemplateUploads().save(server, variant);
        try {
            var files = result.get(60, java.util.concurrent.TimeUnit.SECONDS);
            return ModuleApiResult.ok("Saved " + files + " file(s) of " + serverName + " into the template "
                    + server.getGroup().getName() + "/" + variant);
        } catch (java.util.concurrent.TimeoutException e) {
            return ModuleApiResult.accepted("Still saving " + serverName + "; the console shows when it is done");
        } catch (java.util.concurrent.ExecutionException e) {
            return ModuleApiResult.invalid(e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ModuleApiResult.accepted("Still saving " + serverName);
        }
    }

    // Template files

    private static final long MAX_TEMPLATE_FILE_BYTES = 1024L * 1024 * 1024;
    private final TemplateFiles templateFiles = new TemplateFiles(Path.of("templates"));

    @FunctionalInterface
    private interface TemplateAction {
        ModuleApiResult run() throws java.io.IOException;
    }

    /**
     * Templates of non-static groups and the global ones; static servers keep their files on their wrapper.
     */
    private ModuleApiResult onTemplate(String group, TemplateAction action) {
        if (!group.equalsIgnoreCase(Templates.RESERVED_GROUP_NAME)) {
            var serverGroup = RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(group).orElse(null);
            if (serverGroup == null) {
                return ModuleApiResult.notFound("Group " + group + " does not exist");
            }
            if (serverGroup.isStaticServer()) {
                return ModuleApiResult.invalid("Static groups keep their files on their wrapper and have no template");
            }
        }
        try {
            return action.run();
        } catch (TemplateFiles.TemplateFileException e) {
            return e.isNotFound() ? ModuleApiResult.notFound(e.getMessage()) : ModuleApiResult.invalid(e.getMessage());
        } catch (java.io.IOException e) {
            return ModuleApiResult.invalid(e.getMessage());
        }
    }

    @Override
    public JsonArray getTemplates() {
        var array = new JsonArray();
        for (var group : RexlCloudMaster.getInstance().getServerGroupManager().getServerGroups()) {
            if (group.isStaticServer()) {
                continue;
            }
            try {
                for (var variant : templateFiles.variants(group.getName())) {
                    var json = new JsonObject();
                    json.addProperty("group", group.getName());
                    json.addProperty("variant", variant);
                    json.addProperty("type", group.getType().name());
                    array.add(json);
                }
            } catch (java.io.IOException | IllegalArgumentException ignored) {
                // A group whose folder cannot be read is left out
            }
        }
        for (var variant : List.of("server", "proxy")) {
            var json = new JsonObject();
            json.addProperty("group", Templates.RESERVED_GROUP_NAME);
            json.addProperty("variant", variant);
            json.addProperty("type", variant.toUpperCase(Locale.ROOT));
            array.add(json);
        }
        return array;
    }

    @Override
    public ModuleApiResult listTemplateFiles(@NonNull String group, @NonNull String variant, @Nullable String path) {
        return onTemplate(group, () -> ModuleApiResult.ok(templateFiles.list(group, variant, path)));
    }

    @Override
    public ModuleApiResult templateFile(@NonNull String group, @NonNull String variant, @NonNull String path) {
        return onTemplate(group, () -> ModuleApiResult.ok(
                new JsonPrimitive(templateFiles.file(group, variant, path).toAbsolutePath().toString())));
    }

    @Override
    public ModuleApiResult writeTemplateFile(@NonNull String group, @NonNull String variant, @NonNull String path,
                                             @NonNull java.io.InputStream content) {
        return onTemplate(group, () -> {
            var size = templateFiles.write(group, variant, path, content, MAX_TEMPLATE_FILE_BYTES);
            Console.info("Template file %s/%s/%s saved (%s bytes)", group, variant, path, size);
            return ModuleApiResult.ok("Saved " + path);
        });
    }

    @Override
    public ModuleApiResult createTemplateFolder(@NonNull String group, @NonNull String variant, @NonNull String path) {
        return onTemplate(group, () -> {
            templateFiles.createFolder(group, variant, path);
            return ModuleApiResult.ok("Created " + path);
        });
    }

    @Override
    public ModuleApiResult deleteTemplateFile(@NonNull String group, @NonNull String variant, @Nullable String path) {
        return onTemplate(group, () -> {
            templateFiles.delete(group, variant, path == null ? "" : path);
            Console.info("Template %s/%s: deleted %s", group, variant, path == null || path.isBlank() ? "the variant" : path);
            return ModuleApiResult.ok("Deleted");
        });
    }

    @Override
    public Optional<AutoCloseable> subscribeConsole(@NonNull String serverName, @NonNull Consumer<String> listener) {
        return RexlCloudMaster.getInstance().getServerManager().getServerByName(serverName)
                .map(server -> server.getLogList().addListener(listener));
    }

    // Server groups

    @Override
    public JsonArray getServerJars() {
        var jsonArray = new JsonArray();
        RexlCloudMaster.getInstance().getServerJarManager().getServerJars().forEach(serverJar -> {
            var jsonObject = new JsonObject();
            jsonObject.addProperty("id", serverJar.getFullText());
            jsonObject.addProperty("name", serverJar.getName());
            jsonObject.addProperty("minecraft_version", serverJar.getMinecraftVersion());
            jsonObject.addProperty("version", serverJar.getVersion());
            jsonObject.addProperty("type", serverJar.getServerType().name());
            // Built with BuildTools on first use, which takes minutes instead of a download
            jsonObject.addProperty("built", serverJar.getSource() == ServerJar.Source.SPIGOT_BUILD_TOOLS);
            // Nothing is installed, the user puts their own server.jar into the template
            jsonObject.addProperty("custom", serverJar.getSource() == ServerJar.Source.CUSTOM);
            jsonArray.add(jsonObject);
        });
        return jsonArray;
    }

    @Override
    public ModuleApiResult createServerGroup(@NonNull String name, @NonNull String type, int memory,
                                             boolean staticServer, @Nullable String wrapper, int minServers,
                                             boolean fallback, @NonNull String serverJar) {
        var master = RexlCloudMaster.getInstance();
        if (!GROUP_NAME.matcher(name).matches()) {
            return ModuleApiResult.invalid("The name may only contain letters, numbers, '-' and '_' (max. 32)");
        }
        if (name.equalsIgnoreCase(Templates.RESERVED_GROUP_NAME)) {
            return ModuleApiResult.invalid("The name " + name + " is reserved for the global templates");
        }
        if (master.getServerGroupManager().existServerGroup(name)) {
            return ModuleApiResult.conflict("A group named " + name + " already exists");
        }
        ServerType serverType;
        try {
            serverType = ServerType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ModuleApiResult.invalid("Unknown group type " + type);
        }
        if (memory < 256) {
            return ModuleApiResult.invalid("Each server needs at least 256 MB of memory");
        }
        if (staticServer && (wrapper == null || master.getWrapperManager().getWrapperByName(wrapper) == null)) {
            return ModuleApiResult.invalid("Static groups need a connected wrapper");
        }
        if (!staticServer && minServers < 0) {
            return ModuleApiResult.invalid("The minimum number of servers cannot be negative");
        }
        if (!master.getServerJarManager().hasServerJar(serverJar)) {
            return ModuleApiResult.invalid("Unknown server jar " + serverJar);
        }
        var jar = master.getServerJarManager().getServerJar(serverJar);
        if (jar.getServerType() != serverType) {
            return ModuleApiResult.invalid("The server jar " + serverJar + " is not a " + serverType.name() + " jar");
        }
        // Downloading the jar can take a while, so the group is created in the background
        executor.submit(() -> {
            try {
                master.getServerGroupManager().createServerGroup(name, serverType, memory, staticServer, wrapper,
                        minServers, fallback, jar);
                Console.info("Created the group %s", name);
            } catch (Exception e) {
                Console.error("Could not create the group %s: %s", name, e.getMessage());
            }
        });
        return ModuleApiResult.accepted("The group " + name + " is being created");
    }

    @Override
    public ModuleApiResult editServerGroup(@NonNull String name, @Nullable Integer memory,
                                           @Nullable Integer maxPlayers, @Nullable Integer minServers,
                                           @Nullable Boolean fallback, @Nullable Boolean removeOldLogs) {
        var groupManager = RexlCloudMaster.getInstance().getServerGroupManager();
        var group = groupManager.getServerGroupByName(name).orElse(null);
        if (group == null) {
            return ModuleApiResult.notFound("Group " + name + " does not exist");
        }
        if (memory != null && memory <= 100) {
            return ModuleApiResult.invalid("Each server needs more than 100 MB of memory");
        }
        if (maxPlayers != null && maxPlayers < 0) {
            return ModuleApiResult.invalid("The maximum number of players cannot be negative");
        }
        if (minServers != null && minServers < 0) {
            return ModuleApiResult.invalid("The minimum number of servers cannot be negative");
        }
        if (memory != null) group.setMemory(memory);
        if (maxPlayers != null) group.setMaxPlayers(maxPlayers);
        if (minServers != null) group.setMinServers(minServers);
        if (fallback != null) group.setFallback(fallback);
        if (removeOldLogs != null) group.setRemoveOldLogs(removeOldLogs);
        groupManager.save();
        return ModuleApiResult.ok();
    }

    @Override
    public JsonArray getJavaRuntimes() {
        var runtimes = new LinkedHashMap<String, JsonObject>();
        RexlCloudMaster.getInstance().getWrapperManager().getWrappers().values().stream()
                .sorted(Comparator.comparing(Wrapper::getName))
                .forEach(wrapper -> wrapper.getJavaRuntimes().forEach(runtime -> {
                    var json = runtimes.computeIfAbsent(runtime.executable(), path -> {
                        var object = new JsonObject();
                        object.addProperty("path", runtime.executable());
                        object.addProperty("version", runtime.version());
                        object.addProperty("major", runtime.major());
                        object.addProperty("vendor", runtime.vendor());
                        object.addProperty("name", runtime.displayName());
                        object.add("wrappers", new JsonArray());
                        return object;
                    });
                    json.getAsJsonArray("wrappers").add(wrapper.getName());
                }));
        var jsonArray = new JsonArray();
        runtimes.values().stream()
                .sorted(Comparator.comparingInt((JsonObject runtime) -> runtime.get("major").getAsInt()).reversed()
                        .thenComparing(runtime -> runtime.get("path").getAsString()))
                .forEach(jsonArray::add);
        return jsonArray;
    }

    @Override
    public ModuleApiResult setServerGroupJavaRuntime(@NonNull String name, @NonNull String javaCommand) {
        var groupManager = RexlCloudMaster.getInstance().getServerGroupManager();
        var group = groupManager.getServerGroupByName(name).orElse(null);
        if (group == null) {
            return ModuleApiResult.notFound("Group " + name + " does not exist");
        }
        var command = javaCommand.trim();
        if (!RexlCloudMaster.getInstance().getWrapperManager().isKnownJavaCommand(command)) {
            return ModuleApiResult.invalid("Unknown Java runtime " + command
                    + ": use \"java\" or a runtime that a connected wrapper reported");
        }
        group.setJavaCommand(command);
        groupManager.save();
        return ModuleApiResult.ok();
    }

    @Override
    public ModuleApiResult deleteServerGroup(@NonNull String name) {
        var groupManager = RexlCloudMaster.getInstance().getServerGroupManager();
        var group = groupManager.getServerGroupByName(name).orElse(null);
        if (group == null) {
            return ModuleApiResult.notFound("Group " + name + " does not exist");
        }
        groupManager.removeServerGroup(group);
        RexlCloudMaster.getInstance().getServerManager().stopServersFromGroup(group);
        return ModuleApiResult.ok();
    }

    @Override
    public ModuleApiResult syncServerGroupTemplate(@NonNull String name) {
        var master = RexlCloudMaster.getInstance();
        var group = master.getServerGroupManager().getServerGroupByName(name).orElse(null);
        if (group == null) {
            return ModuleApiResult.notFound("Group " + name + " does not exist");
        }
        if (group.isStaticServer()) {
            return ModuleApiResult.invalid("Static groups have no shared template");
        }
        // Every wrapper reports its templates again, so files changed by hand on a wrapper are repaired too
        master.getTemplateSync().verifyAll();
        return ModuleApiResult.accepted("The templates are being checked and sent");
    }

    // Players

    @Override
    public JsonArray getPlayers() {
        var jsonArray = new JsonArray();
        RexlCloudMaster.getInstance().getPlayerManager().getPlayers().stream()
                .sorted(Comparator.comparing(player -> player.getName().toLowerCase(Locale.ROOT)))
                .forEach(player -> {
                    var jsonObject = new JsonObject();
                    jsonObject.addProperty("uuid", player.getUuid().toString());
                    jsonObject.addProperty("name", player.getName());
                    jsonObject.addProperty("server", player.getServer() == null ? null : player.getServer().getName());
                    jsonObject.addProperty("server_group", player.getServer() == null || player.getServer().getServerGroup() == null
                            ? null : player.getServer().getServerGroup().getName());
                    jsonObject.addProperty("proxy", player.getProxyServer() == null ? null : player.getProxyServer().getName());
                    jsonObject.addProperty("login", player.getLogin());
                    jsonArray.add(jsonObject);
                });
        return jsonArray;
    }

    @Override
    public ModuleApiResult kickPlayer(@NonNull UUID uuid, @Nullable String message) {
        return sendToPlayerProxy(uuid, proxyName -> new PacketContainer(PacketType.PLAYER_KICK,
                new PlayerKickPacket(uuid, proxyName, message == null ? "" : message)));
    }

    @Override
    public ModuleApiResult messagePlayer(@NonNull UUID uuid, @NonNull String message) {
        // The proxy expects a JSON chat component; the text may use MiniMessage and & colour codes
        var json = CloudText.toJson(message);
        return sendToPlayerProxy(uuid, proxyName -> new PacketContainer(PacketType.PLAYER_SEND_MESSAGE,
                new PlayerMessagePacket(uuid, proxyName, json)));
    }

    private static final int MAX_DATA_KEY_LENGTH = 255;

    @Override
    public ModuleApiResult getPlayer(@NonNull String player) {
        var found = findPlayer(player);
        if (found.isEmpty()) {
            return ModuleApiResult.notFound("Player " + player + " never joined the network");
        }
        var offline = found.get();
        var json = new JsonObject();
        json.addProperty("uuid", offline.getUuid().toString());
        json.addProperty("name", offline.getName());
        json.addProperty("first_login", offline.getFirstLogin());
        json.addProperty("last_login", offline.getLastLogin());
        var online = RexlCloudMaster.getInstance().getPlayerManager().getPlayer(offline.getUuid());
        json.addProperty("online", online.isPresent());
        online.ifPresent(onlinePlayer -> {
            json.addProperty("server", onlinePlayer.getServer() == null ? null : onlinePlayer.getServer().getName());
            json.addProperty("server_group", onlinePlayer.getServer() == null || onlinePlayer.getServer().getServerGroup() == null
                    ? null : onlinePlayer.getServer().getServerGroup().getName());
            json.addProperty("proxy", onlinePlayer.getProxyServer() == null ? null : onlinePlayer.getProxyServer().getName());
            json.addProperty("login", onlinePlayer.getLogin());
        });
        var data = new JsonArray();
        offline.getData().stream()
                .sorted(Comparator.comparing(PlayerDatabaseData::getKey))
                .forEach(entry -> data.add(PlayerDataValues.describe(entry)));
        json.add("data", data);
        return ModuleApiResult.ok(json);
    }

    @Override
    public ModuleApiResult setPlayerData(@NonNull String player, @NonNull String key, @Nullable String type,
                                         @NonNull JsonElement value) {
        if (key.isBlank() || key.length() > MAX_DATA_KEY_LENGTH) {
            return ModuleApiResult.invalid("The key must have 1 to " + MAX_DATA_KEY_LENGTH + " characters");
        }
        var found = findPlayer(player);
        if (found.isEmpty()) {
            return ModuleApiResult.notFound("Player " + player + " never joined the network");
        }
        var offline = found.get();
        var current = offline.getData().stream().filter(entry -> entry.getKey().equals(key)).findFirst();
        var chosenType = type != null ? type
                : current.map(entry -> PlayerDataValues.writableType(entry.getValue()))
                .orElseGet(() -> PlayerDataValues.guessType(value));
        if (chosenType == null) {
            chosenType = PlayerDataValues.guessType(value);
        }
        byte[] bytes;
        try {
            bytes = PlayerDataValues.encode(chosenType, value);
        } catch (IllegalArgumentException e) {
            return ModuleApiResult.invalid(e.getMessage());
        }
        await(RexlCloudMaster.getInstance().getPlayerManager().updateData(offline.getUuid(), key, bytes, null));
        return ModuleApiResult.ok(PlayerDataValues.describe(new PlayerDatabaseData(offline.getUuid(), key, bytes)));
    }

    @Override
    public ModuleApiResult removePlayerData(@NonNull String player, @NonNull String key) {
        var found = findPlayer(player);
        if (found.isEmpty()) {
            return ModuleApiResult.notFound("Player " + player + " never joined the network");
        }
        var offline = found.get();
        if (!offline.hasData(key)) {
            return ModuleApiResult.notFound(offline.getName() + " has no value " + key);
        }
        await(RexlCloudMaster.getInstance().getPlayerManager().updateData(offline.getUuid(), key, null, null));
        return ModuleApiResult.ok();
    }

    private static Optional<CloudOfflinePlayer> findPlayer(String player) {
        var playerManager = RexlCloudMaster.getInstance().getPlayerManager();
        UUID uuid = null;
        try {
            uuid = UUID.fromString(player);
        } catch (IllegalArgumentException e) {
            // a name
        }
        return await(uuid != null ? playerManager.getOfflinePlayer(uuid) : playerManager.getOfflinePlayer(player));
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the database", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("The database failed: " + e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("The database did not answer within 10 seconds", e);
        }
    }

    private static final int MAX_MOTD_LENGTH = 1000;

    @Override
    public String renderText(@NonNull String text) {
        return CloudText.toJson(text);
    }

    @Override
    public JsonObject getMotd() {
        var master = RexlCloudMaster.getInstance();
        var messages = master.getPluginMessageConfig().messages();
        var json = new JsonObject();
        json.addProperty("motd", messages.get(MessageKey.MOTD));
        json.addProperty("maintenance_motd", messages.get(MessageKey.MAINTENANCE_MOTD));
        json.addProperty("maintenance", master.getMasterConfig().maintenance());
        return json;
    }

    @Override
    public ModuleApiResult setMotd(@Nullable String motd, @Nullable String maintenanceMotd) {
        for (var text : new String[]{motd, maintenanceMotd}) {
            if (text != null && text.length() > MAX_MOTD_LENGTH) {
                return ModuleApiResult.invalid("The MOTD may have at most " + MAX_MOTD_LENGTH + " characters");
            }
        }
        RexlCloudMaster.getInstance().updateMessages(Map.of(
                MessageKey.MOTD, motd == null ? getMotd().get("motd").getAsString() : motd,
                MessageKey.MAINTENANCE_MOTD, maintenanceMotd == null ? getMotd().get("maintenance_motd").getAsString() : maintenanceMotd));
        return ModuleApiResult.ok();
    }

    private ModuleApiResult sendToPlayerProxy(UUID uuid, Function<String, PacketContainer> packet) {
        var master = RexlCloudMaster.getInstance();
        var player = master.getPlayerManager().getPlayer(uuid).orElse(null);
        if (player == null) {
            return ModuleApiResult.notFound("Player " + uuid + " is not online");
        }
        var proxy = player.getProxyServer();
        var wrapper = master.getWrapperManager().getWrapperFromApiWrapper(proxy.getWrapper()).orElse(null);
        if (wrapper == null) {
            return ModuleApiResult.notFound("The wrapper of proxy " + proxy.getName() + " is not connected");
        }
        wrapper.sendPacket(packet.apply(proxy.getName()));
        return ModuleApiResult.ok();
    }

    // Wrappers

    @Override
    public JsonArray getWrappers() {
        var master = RexlCloudMaster.getInstance();
        var wrapperManager = master.getWrapperManager();
        var jsonArray = new JsonArray();
        wrapperManager.getWrapperRegistry().values().stream()
                .sorted(Comparator.comparing(WrapperCredentials::getName))
                .forEach(credentials -> {
                    var jsonObject = new JsonObject();
                    jsonObject.addProperty("name", credentials.getName());
                    jsonObject.addProperty("address", credentials.getAddress());
                    var wrapper = wrapperManager.getWrappers().get(credentials.getName());
                    jsonObject.addProperty("connected", wrapper != null);
                    var servers = new JsonArray();
                    if (wrapper != null) {
                        jsonObject.addProperty("in_shutdown", wrapper.isInShutdown());
                        jsonObject.addProperty("cpu_usage", wrapper.getCpuUsage());
                        jsonObject.addProperty("used_memory", wrapper.getUsedMemory());
                        jsonObject.addProperty("free_memory", wrapper.getFreeMemory());
                        jsonObject.addProperty("total_memory", wrapper.getTotalMemory());
                        master.getServerManager().getServers().stream()
                                .filter(server -> wrapper.equals(server.getWrapper()))
                                .forEach(server -> servers.add(server.getName()));
                    }
                    jsonObject.add("servers", servers);
                    jsonArray.add(jsonObject);
                });
        return jsonArray;
    }

    @Override
    public ModuleApiResult addWrapper(@NonNull String address, @Nullable UUID key) {
        if (address.isBlank()) {
            return ModuleApiResult.invalid("The address cannot be empty");
        }
        var wrapperManager = RexlCloudMaster.getInstance().getWrapperManager();
        var credentials = new WrapperCredentials(wrapperManager.getNewWrapperName(), address.trim(),
                key == null ? UUID.randomUUID() : key);
        if (!wrapperManager.addWrapperCredentials(credentials)) {
            return ModuleApiResult.conflict("A wrapper named " + credentials.getName() + " already exists");
        }
        var data = new JsonObject();
        data.addProperty("name", credentials.getName());
        data.addProperty("address", credentials.getAddress());
        data.addProperty("key", credentials.getKey().toString());
        return ModuleApiResult.ok(data);
    }

    @Override
    public ModuleApiResult stopWrapper(@NonNull String name) {
        var wrapperManager = RexlCloudMaster.getInstance().getWrapperManager();
        var wrapper = wrapperManager.getWrapperByName(name);
        if (wrapper == null) {
            return ModuleApiResult.notFound("Wrapper " + name + " is not connected");
        }
        wrapperManager.stopWrapper(wrapper);
        return ModuleApiResult.accepted("Wrapper " + name + " is stopping");
    }

    // Maintenance

    @Override
    public boolean isMaintenance() {
        return RexlCloudMaster.getInstance().getMasterConfig().maintenance();
    }

    @Override
    public void setMaintenance(boolean maintenance) {
        RexlCloudMaster.getInstance().setMaintenance(maintenance);
    }

    // Modules

    @Override
    public JsonObject getModules() {
        var moduleManager = ModuleManager.getInstance();
        var loaded = new JsonArray();
        moduleManager.getModules().forEach(module -> {
            var info = module.getModuleInfo();
            var jsonObject = new JsonObject();
            jsonObject.addProperty("name", info.getName());
            jsonObject.addProperty("version", info.getVersion());
            jsonObject.addProperty("author", info.getAuthor());
            jsonObject.addProperty("main_class", info.getMainClass());
            loaded.add(jsonObject);
        });
        var files = new JsonArray();
        moduleManager.getModuleFileNames().forEach(files::add);
        var jsonObject = new JsonObject();
        jsonObject.add("loaded", loaded);
        jsonObject.add("files", files);
        return jsonObject;
    }

    @Override
    public ModuleApiResult loadModule(@NonNull String fileName) {
        var moduleManager = ModuleManager.getInstance();
        // Only files that are actually in the module folder may be loaded
        if (!moduleManager.getModuleFileNames().contains(fileName)) {
            return ModuleApiResult.notFound("There is no module file " + fileName);
        }
        var result = moduleManager.loadModule(fileName);
        return switch (result.getType()) {
            case SUCCESS -> ModuleApiResult.ok();
            case ALREADY_LOADED -> ModuleApiResult.conflict("The module " + result.getModuleName() + " is already loaded");
            case LOAD_FAILED -> ModuleApiResult.invalid("The module " + result.getModuleName() + " could not be loaded");
            case NOT_A_FILE -> ModuleApiResult.invalid(fileName + " is not a module jar");
        };
    }

    @Override
    public ModuleApiResult reloadModule(@NonNull String name) {
        var moduleManager = ModuleManager.getInstance();
        var module = moduleManager.getModuleByName(name).orElse(null);
        if (module == null) {
            return ModuleApiResult.notFound("The module " + name + " is not loaded");
        }
        moduleManager.reloadModule(module);
        return ModuleApiResult.ok();
    }

    @Override
    public ModuleApiResult disableModule(@NonNull String name) {
        var moduleManager = ModuleManager.getInstance();
        var module = moduleManager.getModuleByName(name).orElse(null);
        if (module == null) {
            return ModuleApiResult.notFound("The module " + name + " is not loaded");
        }
        moduleManager.disableModule(module);
        return ModuleApiResult.ok();
    }

}
