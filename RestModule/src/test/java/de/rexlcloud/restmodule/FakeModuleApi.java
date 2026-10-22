package de.rexlcloud.restmodule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.rexlcloud.core.api.module.ModuleApi;
import de.rexlcloud.core.api.module.ModuleApiResult;
import de.rexlcloud.core.console.LogList;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory stand-in for the master, used by the tests and the {@link DevServer}.
 */
public class FakeModuleApi implements ModuleApi {

    final Map<String, Group> groups = new LinkedHashMap<>();
    final Map<String, Server> servers = new LinkedHashMap<>();
    final Map<String, Wrapper> wrappers = new LinkedHashMap<>();
    final List<Player> players = new CopyOnWriteArrayList<>();
    final List<String> executedCommands = new CopyOnWriteArrayList<>();
    final List<String> loadedModules = new CopyOnWriteArrayList<>(List.of("RestModule", "SignModule"));
    final List<String> moduleFiles = new CopyOnWriteArrayList<>(List.of("RestModule.jar", "SignModule.jar", "StatsModule.jar"));
    final List<String> killedServers = new CopyOnWriteArrayList<>();
    final List<String> messages = new CopyOnWriteArrayList<>();
    volatile boolean maintenance = true;

    public FakeModuleApi() {
        wrappers.put("Wrapper-1", new Wrapper("Wrapper-1", "10.0.0.11", true));
        wrappers.put("Wrapper-2", new Wrapper("Wrapper-2", "10.0.0.12", false));
        addGroup(new Group("Proxy", "PROXY", 512, false, null, 1, false));
        addGroup(new Group("Lobby", "SERVER", 1024, false, null, 2, true));
        addGroup(new Group("BedWars", "SERVER", 2048, false, null, 1, false));
        addGroup(new Group("Build", "SERVER", 2048, true, "Wrapper-1", 1, false));
        for (var group : groups.values()) {
            for (int i = 1; i <= group.minServers; i++) {
                startServer(group);
            }
        }
        var lobby = servers.get("Lobby-1");
        var proxy = servers.get("Proxy-1");
        for (var name : List.of("rexlNico", "Steve", "Alex")) {
            players.add(new Player(UUID.nameUUIDFromBytes(name.getBytes()), name, lobby.name, proxy.name,
                    System.currentTimeMillis() - 600_000));
        }
    }

    private void addGroup(Group group) {
        groups.put(group.name, group);
    }

    private Server startServer(Group group) {
        var index = 1;
        while (servers.containsKey(group.name + "-" + index)) {
            index++;
        }
        var server = new Server(group.name + "-" + index, group,
                ("PROXY".equals(group.type) ? 25565 : 25700) + servers.size(), "Wrapper-1");
        server.console.add("\u001b[32m[INFO]\u001b[0m Starting " + server.name);
        server.console.add("[INFO] Done (1.234s)! For help, type \"help\"");
        servers.put(server.name, server);
        return server;
    }

    @Override
    public synchronized JsonArray getServerStats(int limit) {
        var array = new JsonArray();
        var now = System.currentTimeMillis();
        for (int i = 0; i < limit; i++) {
            var json = new JsonObject();
            json.addProperty("time", now - i * 60_000L);
            json.addProperty("cpu_usage", 0.2 + (i % 10) / 50.0);
            // The master stores memory stats in MB
            json.addProperty("used_memory", 4_000 + (i % 7) * 100);
            json.addProperty("free_memory", 12_000 - (i % 7) * 100);
            json.addProperty("online_servers", servers.size());
            json.addProperty("started_servers", servers.size());
            json.addProperty("online_players", players.size() + i % 3);
            array.add(json);
        }
        return array;
    }

    @Override
    public synchronized boolean execute(String serverName, String command) {
        var server = servers.get(serverName);
        if (server == null) {
            return false;
        }
        executedCommands.add(serverName + ":" + command);
        server.console.add("> " + command);
        return true;
    }

    @Override
    public synchronized boolean stop(String serverName) {
        return servers.remove(serverName) != null;
    }

    @Override
    public synchronized boolean start(String serverGroup, int amount) {
        var group = groups.get(serverGroup);
        if (group == null || amount < 1) {
            return false;
        }
        for (int i = 0; i < amount; i++) {
            startServer(group);
        }
        return true;
    }

    @Override
    public synchronized JsonArray getServerInfos(@Nullable String serverName) {
        var array = new JsonArray();
        servers.values().stream()
                .filter(server -> serverName == null || serverName.isBlank() || server.name.equals(serverName))
                .forEach(server -> {
                    var json = new JsonObject();
                    json.addProperty("server_name", server.name);
                    json.addProperty("server_port", server.port);
                    json.addProperty("server_state", server.state);
                    json.addProperty("wrapper", server.wrapper);
                    json.addProperty("server_group", server.group.name);
                    json.addProperty("server_type", server.group.type);
                    var names = new JsonArray();
                    players.stream().filter(p -> p.server.equals(server.name) || p.proxy.equals(server.name))
                            .forEach(p -> names.add(p.name));
                    json.addProperty("online_players", names.size());
                    json.addProperty("max_players", server.group.maxPlayers);
                    json.addProperty("memory", server.group.memory);
                    json.addProperty("static", server.group.staticServer);
                    json.addProperty("online_state", server.onlineState);
                    json.add("player_names", names);
                    array.add(json);
                });
        return array;
    }

    @Override
    public synchronized JsonArray getServerGroupsInfos(@Nullable String groupName) {
        var array = new JsonArray();
        groups.values().stream()
                .filter(group -> groupName == null || groupName.isBlank() || group.name.equals(groupName))
                .forEach(group -> {
                    var json = new JsonObject();
                    json.addProperty("group_name", group.name);
                    json.addProperty("group_type", group.type);
                    json.addProperty("group_memory", group.memory);
                    json.addProperty("group_java_args", "");
                    json.addProperty("group_java_cmd", group.javaCommand);
                    json.addProperty("group_map_players", group.maxPlayers);
                    json.addProperty("group_static", group.staticServer);
                    json.addProperty("group_fallback", group.fallback);
                    json.addProperty("group_remove_old_logs", group.removeOldLogs);
                    json.addProperty("group_wrapper", group.wrapper);
                    json.addProperty("group_min_servers", group.minServers);
                    var serverNames = new JsonArray();
                    var online = 0;
                    for (var server : servers.values()) {
                        if (server.group == group) {
                            serverNames.add(server.name);
                            online += (int) players.stream().filter(p -> p.server.equals(server.name)).count();
                        }
                    }
                    json.add("servers", serverNames);
                    json.addProperty("online_players", online);
                    array.add(json);
                });
        return array;
    }

    @Override
    public synchronized String[] getServerConsole(@NonNull String serverName) {
        var server = servers.get(serverName);
        return server == null ? new String[0] : server.console.snapshot().toArray(String[]::new);
    }

    @Override
    public synchronized JsonObject getOverview() {
        var json = new JsonObject();
        json.addProperty("servers_total", servers.size());
        json.addProperty("servers_online", servers.values().stream().filter(s -> "online".equals(s.onlineState)).count());
        json.addProperty("proxies_online", servers.values().stream().filter(s -> "PROXY".equals(s.group.type)).count());
        json.addProperty("players_online", players.size());
        json.addProperty("groups", groups.size());
        json.addProperty("wrappers_registered", wrappers.size());
        json.addProperty("wrappers_connected", wrappers.values().stream().filter(w -> w.connected).count());
        json.addProperty("maintenance", maintenance);
        json.addProperty("version", "dev");
        return json;
    }

    @Override
    public synchronized ModuleApiResult kill(@NonNull String serverName) {
        if (servers.remove(serverName) == null) {
            return ModuleApiResult.notFound("Server " + serverName + " does not exist");
        }
        killedServers.add(serverName);
        return ModuleApiResult.accepted("Server " + serverName + " is being killed");
    }

    @Override
    public synchronized ModuleApiResult restart(@NonNull String serverName) {
        var server = servers.get(serverName);
        if (server == null) {
            return ModuleApiResult.notFound("Server " + serverName + " does not exist");
        }
        server.console.add("[INFO] Restarting");
        return ModuleApiResult.accepted("Server " + serverName + " is restarting");
    }

    @Override
    public synchronized ModuleApiResult saveServerToTemplate(@NonNull String serverName, @NonNull String variant) {
        if (!servers.containsKey(serverName)) {
            return ModuleApiResult.notFound("Server " + serverName + " does not exist");
        }
        savedTemplates.add(serverName + ":" + variant);
        return ModuleApiResult.ok("Saved 3 file(s) of " + serverName + " into the template " + variant);
    }

    /**
     * Template files, by "group/variant/path".
     */
    public final java.util.Map<String, byte[]> templateFiles = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public JsonArray getTemplates() {
        var array = new JsonArray();
        var json = new JsonObject();
        json.addProperty("group", "Lobby");
        json.addProperty("variant", "default");
        json.addProperty("type", "SERVER");
        array.add(json);
        return array;
    }

    @Override
    public ModuleApiResult listTemplateFiles(@NonNull String group, @NonNull String variant, @Nullable String path) {
        var prefix = group + "/" + variant + "/" + (path == null || path.isBlank() ? "" : path + "/");
        var array = new JsonArray();
        templateFiles.forEach((key, content) -> {
            if (key.startsWith(prefix) && !key.substring(prefix.length()).contains("/")) {
                var json = new JsonObject();
                json.addProperty("name", key.substring(prefix.length()));
                json.addProperty("directory", false);
                json.addProperty("size", content.length);
                array.add(json);
            }
        });
        return ModuleApiResult.ok(array);
    }

    @Override
    public ModuleApiResult templateFile(@NonNull String group, @NonNull String variant, @NonNull String path) {
        var content = templateFiles.get(group + "/" + variant + "/" + path);
        if (content == null) {
            return ModuleApiResult.notFound("The file " + path + " does not exist");
        }
        try {
            var file = java.nio.file.Files.createTempFile("template", ".bin");
            java.nio.file.Files.write(file, content);
            file.toFile().deleteOnExit();
            return ModuleApiResult.ok(new com.google.gson.JsonPrimitive(file.toString()));
        } catch (java.io.IOException e) {
            return ModuleApiResult.invalid(e.getMessage());
        }
    }

    @Override
    public ModuleApiResult writeTemplateFile(@NonNull String group, @NonNull String variant, @NonNull String path,
                                             java.io.@NonNull InputStream content) {
        if (path.contains("..")) {
            return ModuleApiResult.invalid("The path " + path + " is outside the template");
        }
        try {
            templateFiles.put(group + "/" + variant + "/" + path, content.readAllBytes());
            return ModuleApiResult.ok("Saved " + path);
        } catch (java.io.IOException e) {
            return ModuleApiResult.invalid(e.getMessage());
        }
    }

    @Override
    public ModuleApiResult createTemplateFolder(@NonNull String group, @NonNull String variant, @NonNull String path) {
        return ModuleApiResult.ok("Created " + path);
    }

    @Override
    public ModuleApiResult deleteTemplateFile(@NonNull String group, @NonNull String variant, @Nullable String path) {
        return templateFiles.remove(group + "/" + variant + "/" + path) == null
                ? ModuleApiResult.notFound("The path " + path + " does not exist") : ModuleApiResult.ok("Deleted");
    }

    /**
     * Calls of saveServerToTemplate, as "server:variant".
     */
    public final java.util.List<String> savedTemplates = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public synchronized Optional<AutoCloseable> subscribeConsole(@NonNull String serverName, @NonNull Consumer<String> listener) {
        return Optional.ofNullable(servers.get(serverName)).map(server -> server.console.addListener(listener));
    }

    @Override
    public JsonArray getServerJars() {
        var array = new JsonArray();
        for (var jar : List.of(new String[]{"PAPER_1_21_10", "Paper", "1.21.10", "SERVER"},
                new String[]{"VELOCITY_3_4_0", "Velocity", null, "PROXY"})) {
            var json = new JsonObject();
            json.addProperty("id", jar[0]);
            json.addProperty("name", jar[1]);
            json.addProperty("minecraft_version", jar[2]);
            json.addProperty("version", (String) null);
            json.addProperty("type", jar[3]);
            array.add(json);
        }
        return array;
    }

    @Override
    public synchronized ModuleApiResult createServerGroup(@NonNull String name, @NonNull String type, int memory,
                                                          boolean staticServer, @Nullable String wrapper,
                                                          int minServers, boolean fallback, @NonNull String serverJar) {
        if (groups.containsKey(name)) {
            return ModuleApiResult.conflict("A group named " + name + " already exists");
        }
        if (memory < 256) {
            return ModuleApiResult.invalid("Each server needs at least 256 MB of memory");
        }
        var group = new Group(name, type.toUpperCase(), memory, staticServer, wrapper, staticServer ? 1 : minServers, fallback);
        addGroup(group);
        for (int i = 0; i < group.minServers; i++) {
            startServer(group);
        }
        return ModuleApiResult.accepted("The group " + name + " is being created");
    }

    @Override
    public synchronized ModuleApiResult editServerGroup(@NonNull String name, @Nullable Integer memory,
                                                        @Nullable Integer maxPlayers, @Nullable Integer minServers,
                                                        @Nullable Boolean fallback, @Nullable Boolean removeOldLogs) {
        var group = groups.get(name);
        if (group == null) {
            return ModuleApiResult.notFound("Group " + name + " does not exist");
        }
        if (memory != null && memory <= 100) {
            return ModuleApiResult.invalid("Each server needs more than 100 MB of memory");
        }
        if (memory != null) group.memory = memory;
        if (maxPlayers != null) group.maxPlayers = maxPlayers;
        if (minServers != null) group.minServers = minServers;
        if (fallback != null) group.fallback = fallback;
        if (removeOldLogs != null) group.removeOldLogs = removeOldLogs;
        return ModuleApiResult.ok();
    }

    String motd = "<#09add3>A rexlCloud network<newline><gray>Welcome!";
    String maintenanceMotd = "<red>Maintenance";

    @Override
    public synchronized JsonObject getMotd() {
        var json = new JsonObject();
        json.addProperty("motd", motd);
        json.addProperty("maintenance_motd", maintenanceMotd);
        json.addProperty("maintenance", isMaintenance());
        return json;
    }

    @Override
    public synchronized ModuleApiResult setMotd(@Nullable String motd, @Nullable String maintenanceMotd) {
        if (motd != null && motd.length() > 1000) {
            return ModuleApiResult.invalid("The MOTD may have at most 1000 characters");
        }
        if (motd != null) this.motd = motd;
        if (maintenanceMotd != null) this.maintenanceMotd = maintenanceMotd;
        return ModuleApiResult.ok();
    }

    @Override
    public String renderText(@NonNull String text) {
        var json = new JsonObject();
        json.addProperty("text", text.replaceAll("<[^>]+>", ""));
        return json.toString();
    }

    static final String JAVA_25 = "/opt/java/jdk-25/bin/java";

    @Override
    public JsonArray getJavaRuntimes() {
        var runtime = new JsonObject();
        runtime.addProperty("path", JAVA_25);
        runtime.addProperty("version", "25.0.4");
        runtime.addProperty("major", 25);
        runtime.addProperty("vendor", "Eclipse Adoptium");
        runtime.addProperty("name", "Java 25.0.4 (Eclipse Adoptium)");
        var wrappers = new JsonArray();
        wrappers.add("Wrapper-1");
        runtime.add("wrappers", wrappers);
        var array = new JsonArray();
        array.add(runtime);
        return array;
    }

    @Override
    public synchronized ModuleApiResult setServerGroupJavaRuntime(@NonNull String name, @NonNull String javaCommand) {
        var group = groups.get(name);
        if (group == null) {
            return ModuleApiResult.notFound("Group " + name + " does not exist");
        }
        if (!javaCommand.equals("java") && !javaCommand.equals(JAVA_25)) {
            return ModuleApiResult.invalid("Unknown Java runtime " + javaCommand);
        }
        group.javaCommand = javaCommand;
        return ModuleApiResult.ok();
    }

    @Override
    public synchronized ModuleApiResult deleteServerGroup(@NonNull String name) {
        var group = groups.remove(name);
        if (group == null) {
            return ModuleApiResult.notFound("Group " + name + " does not exist");
        }
        servers.values().removeIf(server -> server.group == group);
        return ModuleApiResult.ok();
    }

    @Override
    public synchronized ModuleApiResult syncServerGroupTemplate(@NonNull String name) {
        var group = groups.get(name);
        if (group == null) {
            return ModuleApiResult.notFound("Group " + name + " does not exist");
        }
        if (group.staticServer) {
            return ModuleApiResult.invalid("Static groups have no shared template");
        }
        return ModuleApiResult.accepted("The templates of " + name + " are being sent");
    }

    @Override
    public synchronized JsonArray getPlayers() {
        var array = new JsonArray();
        for (var player : players) {
            var json = new JsonObject();
            json.addProperty("uuid", player.uuid.toString());
            json.addProperty("name", player.name);
            json.addProperty("server", player.server);
            var server = servers.get(player.server);
            json.addProperty("server_group", server == null ? null : server.group.name);
            json.addProperty("proxy", player.proxy);
            json.addProperty("login", player.login);
            array.add(json);
        }
        return array;
    }

    @Override
    public synchronized ModuleApiResult kickPlayer(@NonNull UUID uuid, @Nullable String message) {
        if (!players.removeIf(player -> player.uuid.equals(uuid))) {
            return ModuleApiResult.notFound("Player " + uuid + " is not online");
        }
        return ModuleApiResult.ok();
    }

    @Override
    public synchronized ModuleApiResult messagePlayer(@NonNull UUID uuid, @NonNull String message) {
        if (players.stream().noneMatch(player -> player.uuid.equals(uuid))) {
            return ModuleApiResult.notFound("Player " + uuid + " is not online");
        }
        messages.add(uuid + ":" + message);
        return ModuleApiResult.ok();
    }

    @Override
    public synchronized JsonArray getWrappers() {
        var array = new JsonArray();
        for (var wrapper : wrappers.values()) {
            var json = new JsonObject();
            json.addProperty("name", wrapper.name);
            json.addProperty("address", wrapper.address);
            json.addProperty("connected", wrapper.connected);
            var serverNames = new JsonArray();
            if (wrapper.connected) {
                json.addProperty("in_shutdown", false);
                json.addProperty("cpu_usage", 0.27);
                json.addProperty("used_memory", 6_200_000_000L);
                json.addProperty("free_memory", 9_800_000_000L);
                json.addProperty("total_memory", 16_000_000_000L);
                servers.values().stream().filter(s -> wrapper.name.equals(s.wrapper)).forEach(s -> serverNames.add(s.name));
            }
            json.add("servers", serverNames);
            array.add(json);
        }
        return array;
    }

    @Override
    public synchronized ModuleApiResult addWrapper(@NonNull String address, @Nullable UUID key) {
        var name = "Wrapper-" + (wrappers.size() + 1);
        wrappers.put(name, new Wrapper(name, address, false));
        var data = new JsonObject();
        data.addProperty("name", name);
        data.addProperty("address", address);
        data.addProperty("key", (key == null ? UUID.randomUUID() : key).toString());
        return ModuleApiResult.ok(data);
    }

    @Override
    public synchronized ModuleApiResult stopWrapper(@NonNull String name) {
        var wrapper = wrappers.get(name);
        if (wrapper == null || !wrapper.connected) {
            return ModuleApiResult.notFound("Wrapper " + name + " is not connected");
        }
        wrapper.connected = false;
        return ModuleApiResult.accepted("Wrapper " + name + " is stopping");
    }

    @Override
    public boolean isMaintenance() {
        return maintenance;
    }

    @Override
    public void setMaintenance(boolean maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    public synchronized JsonObject getModules() {
        var loaded = new JsonArray();
        for (var name : loadedModules) {
            var json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("version", "1.0.0");
            json.addProperty("author", "rexlNico");
            json.addProperty("main_class", "de.rexlcloud." + name.toLowerCase());
            loaded.add(json);
        }
        var files = new JsonArray();
        moduleFiles.forEach(files::add);
        var json = new JsonObject();
        json.add("loaded", loaded);
        json.add("files", files);
        return json;
    }

    @Override
    public synchronized ModuleApiResult loadModule(@NonNull String fileName) {
        if (!moduleFiles.contains(fileName)) {
            return ModuleApiResult.notFound("There is no module file " + fileName);
        }
        var name = fileName.replace(".jar", "");
        if (loadedModules.contains(name)) {
            return ModuleApiResult.conflict("The module " + name + " is already loaded");
        }
        loadedModules.add(name);
        return ModuleApiResult.ok();
    }

    @Override
    public synchronized ModuleApiResult reloadModule(@NonNull String name) {
        return loadedModules.contains(name) ? ModuleApiResult.ok() : ModuleApiResult.notFound("The module " + name + " is not loaded");
    }

    @Override
    public synchronized ModuleApiResult disableModule(@NonNull String name) {
        return loadedModules.remove(name) ? ModuleApiResult.ok() : ModuleApiResult.notFound("The module " + name + " is not loaded");
    }

    static final class Group {
        final String name;
        final String type;
        int memory;
        final boolean staticServer;
        final String wrapper;
        int minServers;
        int maxPlayers = 50;
        boolean fallback;
        boolean removeOldLogs;
        String javaCommand = "java";

        Group(String name, String type, int memory, boolean staticServer, String wrapper, int minServers, boolean fallback) {
            this.name = name;
            this.type = type;
            this.memory = memory;
            this.staticServer = staticServer;
            this.wrapper = wrapper;
            this.minServers = minServers;
            this.fallback = fallback;
        }
    }

    static final class Server {
        final String name;
        final Group group;
        final int port;
        final String wrapper;
        final LogList console = new LogList(500);
        String state = "";
        String onlineState = "online";

        Server(String name, Group group, int port, String wrapper) {
            this.name = name;
            this.group = group;
            this.port = port;
            this.wrapper = wrapper;
        }
    }

    record Player(UUID uuid, String name, String server, String proxy, long login) {
    }

    static final class Wrapper {
        final String name;
        final String address;
        boolean connected;

        Wrapper(String name, String address, boolean connected) {
            this.name = name;
            this.address = address;
            this.connected = connected;
        }
    }

    List<Server> serverList() {
        return new ArrayList<>(servers.values());
    }

}
