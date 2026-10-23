package de.rexlcloud.restmodule;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.rexlcloud.restmodule.user.Permission;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the real Javalin server over HTTP and WebSocket against a {@link FakeModuleApi}.
 */
class RestApiTest {

    private static final HttpClient http = HttpClient.newHttpClient();
    private static final FakeModuleApi api = new FakeModuleApi();
    private static String base;
    private static int port;
    private static String adminToken;

    @TempDir
    static Path dataFolder;

    @BeforeAll
    static void startServer() throws Exception {
        port = TestCloud.freePort();
        base = "http://127.0.0.1:" + port + "/api/v2";
        var module = TestCloud.start(dataFolder, "127.0.0.1", port, api);
        assertNotNull(module);
        TestCloud.createUser("tester", "testpassword", Permission.values());
        TestCloud.createUser("viewer", "viewerpassword", Permission.SEE_ALL_SERVERS, Permission.SEE_SERVER_CONSOLE);
        adminToken = login("tester", "testpassword");
    }

    @AfterAll
    static void stopServer() {
        RestModule.getInstance().onDisable();
    }

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        var response = send("GET", "/servers", null, null);
        assertEquals(401, response.statusCode());
        assertEquals("unauthorized", json(response).getAsJsonObject().get("error").getAsString());

        var malformed = HttpRequest.newBuilder(URI.create(base + "/servers")).header("Authorization", "Basic abc").build();
        assertEquals(401, http.send(malformed, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void loginFailsWithWrongPasswordAndIsRateLimited() throws Exception {
        TestCloud.createUser("limited", "rightpassword");
        for (int i = 0; i < 5; i++) {
            var response = send("POST", "/auth/login", null, "{\"username\":\"limited\",\"password\":\"wrong\"}");
            assertEquals(401, response.statusCode());
        }
        var blocked = send("POST", "/auth/login", null, "{\"username\":\"limited\",\"password\":\"rightpassword\"}");
        assertEquals(429, blocked.statusCode());
    }

    @Test
    void returnsCurrentUserAndLogsOut() throws Exception {
        var token = login("tester", "testpassword");
        var me = json(send("GET", "/auth/me", token, null)).getAsJsonObject();
        assertEquals("tester", me.get("username").getAsString());
        assertTrue(me.getAsJsonArray("permissions").size() >= Permission.values().length);

        assertEquals(200, send("POST", "/auth/logout", token, null).statusCode());
        assertEquals(401, send("GET", "/auth/me", token, null).statusCode());
    }

    @Test
    void enforcesPermissions() throws Exception {
        var token = login("viewer", "viewerpassword");
        assertEquals(200, send("GET", "/servers", token, null).statusCode());
        var forbidden = send("POST", "/servers/Lobby-1/stop", token, null);
        assertEquals(403, forbidden.statusCode());
        assertTrue(json(forbidden).getAsJsonObject().get("message").getAsString().contains("STOP_SERVER"));
        assertEquals(403, send("GET", "/players/Steve", token, null).statusCode());
        assertEquals(403, send("PUT", "/players/Steve/data/example:coins", token, "{\"value\":1}").statusCode());
        assertEquals(403, send("DELETE", "/players/Steve/data/example:coins", token, null).statusCode());
    }

    @Test
    void listsServersAndRunsCommands() throws Exception {
        var servers = json(send("GET", "/servers", adminToken, null)).getAsJsonArray();
        assertFalse(servers.isEmpty());

        var server = json(send("GET", "/servers/Lobby-1", adminToken, null)).getAsJsonObject();
        assertEquals("Lobby", server.get("server_group").getAsString());
        assertEquals(404, send("GET", "/servers/Nope-1", adminToken, null).statusCode());

        // Unicode and '$' must survive (the old API sent commands in headers and logged them with replaceFirst)
        var command = send("POST", "/servers/Lobby-1/command", adminToken, "{\"command\":\"say Grüße $1 §a\"}");
        assertEquals(200, command.statusCode(), command.body());
        assertTrue(api.executedCommands.contains("Lobby-1:say Grüße $1 §a"));

        var console = json(send("GET", "/servers/Lobby-1/console", adminToken, null)).getAsJsonObject();
        assertTrue(console.getAsJsonArray("lines").toString().contains("say Grüße $1 §a"));

        assertEquals(400, send("POST", "/servers/Lobby-1/command", adminToken, "{}").statusCode());
        assertEquals(400, send("POST", "/servers/Lobby-1/command", adminToken, "not json").statusCode());
    }

    @Test
    void killsAndRestartsServers() throws Exception {
        assertEquals(202, send("POST", "/groups/BedWars/start", adminToken, "{\"amount\":2}").statusCode());
        assertEquals(202, send("POST", "/servers/BedWars-2/kill", adminToken, null).statusCode());
        assertTrue(api.killedServers.contains("BedWars-2"));
        assertEquals(202, send("POST", "/servers/BedWars-1/restart", adminToken, null).statusCode());
        assertEquals(400, send("POST", "/groups/BedWars/start", adminToken, "{\"amount\":0}").statusCode());
        assertEquals(409, send("POST", "/groups/Build/start", adminToken, null).statusCode());
    }

    @Test
    void changesTheMotd() throws Exception {
        var motd = json(send("GET", "/motd", adminToken, null)).getAsJsonObject();
        assertTrue(motd.get("motd").getAsString().contains("rexlCloud"));

        var changed = send("PUT", "/motd", adminToken, "{\"motd\":\"<gold>Hello<newline><gray>World\"}");
        assertEquals(200, changed.statusCode(), changed.body());
        motd = json(changed).getAsJsonObject();
        assertEquals("<gold>Hello<newline><gray>World", motd.get("motd").getAsString());
        assertEquals("<red>Maintenance", motd.get("maintenance_motd").getAsString(), "unchanged");
        assertEquals(400, send("PUT", "/motd", adminToken, "{}").statusCode());
        var preview = json(send("POST", "/motd/preview", adminToken, "{\"text\":\"<gold>Hi\"}")).getAsJsonObject();
        assertEquals("Hi", preview.getAsJsonObject("component").get("text").getAsString());
        assertEquals(400, send("PUT", "/motd", adminToken, "{\"motd\":\"" + "x".repeat(1001) + "\"}").statusCode());
    }

    @Test
    void managesGroups() throws Exception {
        var jars = json(send("GET", "/server-jars", adminToken, null)).getAsJsonArray();
        assertEquals(2, jars.size());

        var create = send("POST", "/groups", adminToken,
                "{\"name\":\"SkyWars\",\"type\":\"server\",\"memory\":1024,\"min_servers\":1,\"server_jar\":\"PAPER_1_21_10\"}");
        assertEquals(202, create.statusCode(), create.body());
        assertEquals(409, send("POST", "/groups", adminToken,
                "{\"name\":\"SkyWars\",\"type\":\"server\",\"memory\":1024,\"server_jar\":\"PAPER_1_21_10\"}").statusCode());
        assertEquals(400, send("POST", "/groups", adminToken, "{\"name\":\"X\",\"type\":\"server\"}").statusCode());

        assertEquals(200, send("PATCH", "/groups/SkyWars", adminToken, "{\"memory\":2048,\"fallback\":true}").statusCode());
        var group = json(send("GET", "/groups/SkyWars", adminToken, null)).getAsJsonObject();
        assertEquals(2048, group.get("group_memory").getAsInt());
        assertTrue(group.get("group_fallback").getAsBoolean());
        assertEquals(400, send("PATCH", "/groups/SkyWars", adminToken, "{}").statusCode());
        assertEquals(400, send("PATCH", "/groups/SkyWars", adminToken, "{\"memory\":\"lots\"}").statusCode());

        // Java runtimes: only "java" or runtimes the wrappers reported
        var runtimes = json(send("GET", "/java-runtimes", adminToken, null)).getAsJsonArray();
        var java25 = runtimes.get(0).getAsJsonObject().get("path").getAsString();
        assertEquals(25, runtimes.get(0).getAsJsonObject().get("major").getAsInt());
        assertEquals(200, send("PATCH", "/groups/SkyWars", adminToken,
                "{\"java_command\":\"" + java25 + "\",\"memory\":3072}").statusCode());
        group = json(send("GET", "/groups/SkyWars", adminToken, null)).getAsJsonObject();
        assertEquals(java25, group.get("group_java_cmd").getAsString());
        assertEquals(3072, group.get("group_memory").getAsInt());
        var unknown = send("PATCH", "/groups/SkyWars", adminToken, "{\"java_command\":\"/bin/sh\",\"memory\":4096}");
        assertEquals(400, unknown.statusCode());
        group = json(send("GET", "/groups/SkyWars", adminToken, null)).getAsJsonObject();
        assertEquals(3072, group.get("group_memory").getAsInt(), "rejected requests change nothing");
        assertEquals(200, send("PATCH", "/groups/SkyWars", adminToken, "{\"java_command\":\"java\"}").statusCode());

        assertEquals(202, send("POST", "/groups/SkyWars/sync-template", adminToken, null).statusCode());
        assertEquals(400, send("POST", "/groups/Build/sync-template", adminToken, null).statusCode());
        assertEquals(200, send("DELETE", "/groups/SkyWars", adminToken, null).statusCode());
        assertEquals(404, send("GET", "/groups/SkyWars", adminToken, null).statusCode());
    }

    @Test
    void managesPlayers() throws Exception {
        var players = json(send("GET", "/players", adminToken, null)).getAsJsonArray();
        var steve = players.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(p -> p.get("name").getAsString().equals("Steve")).findFirst().orElseThrow();
        var uuid = steve.get("uuid").getAsString();

        assertEquals(200, send("POST", "/players/" + uuid + "/message", adminToken, "{\"message\":\"Hi\"}").statusCode());
        assertTrue(api.messages.contains(uuid + ":Hi"));
        assertEquals(400, send("POST", "/players/not-a-uuid/kick", adminToken, null).statusCode());
        assertEquals(200, send("POST", "/players/" + uuid + "/kick", adminToken, "{\"message\":\"Bye\"}").statusCode());
        assertEquals(404, send("POST", "/players/" + uuid + "/kick", adminToken, null).statusCode());
    }

    @Test
    void readsAndWritesPlayerData() throws Exception {
        var steve = json(send("GET", "/players/steve", adminToken, null)).getAsJsonObject().getAsJsonObject("data");
        assertEquals("Steve", steve.get("name").getAsString());
        assertTrue(steve.get("online").getAsBoolean());
        var values = steve.getAsJsonArray("data").asList().stream().map(JsonElement::getAsJsonObject).toList();
        var coins = value(values, "example:coins");
        assertEquals("int", coins.get("type").getAsString());
        assertEquals(250, coins.get("value").getAsInt());
        assertTrue(coins.get("editable").getAsBoolean());
        var friends = value(values, "example:friends");
        assertEquals("list", friends.get("type").getAsString());
        assertEquals("Alex", friends.getAsJsonArray("value").get(0).getAsString());
        assertFalse(friends.get("editable").getAsBoolean());
        var stats = value(values, "stats:bedwars");
        assertFalse(stats.get("readable").getAsBoolean(), "a plugin's own class is not read");
        assertEquals(FakeModuleApi.Stats.class.getName(), stats.get("type").getAsString());

        // Offline players by UUID; the type of an existing value is kept
        var herobrine = UUID.nameUUIDFromBytes("Herobrine".getBytes()).toString();
        var offline = json(send("GET", "/players/" + herobrine, adminToken, null)).getAsJsonObject().getAsJsonObject("data");
        assertFalse(offline.get("online").getAsBoolean());
        var set = send("PUT", "/players/" + herobrine + "/data/example:coins", adminToken, "{\"value\":\"9100\"}");
        assertEquals(200, set.statusCode());
        var stored = json(set).getAsJsonObject().getAsJsonObject("data");
        assertEquals("int", stored.get("type").getAsString());
        assertEquals(9100, stored.get("value").getAsInt());

        assertEquals(200, send("PUT", "/players/Herobrine/data/example:title", adminToken,
                "{\"type\":\"string\",\"value\":\"Legend\"}").statusCode());
        assertEquals(400, send("PUT", "/players/Herobrine/data/example:visits", adminToken,
                "{\"type\":\"int\",\"value\":\"many\"}").statusCode());
        assertEquals(400, send("PUT", "/players/Herobrine/data/example:visits", adminToken,
                "{\"type\":\"object\",\"value\":1}").statusCode());
        assertEquals(400, send("PUT", "/players/Herobrine/data/example:visits", adminToken, "{}").statusCode());
        assertEquals(200, send("DELETE", "/players/Herobrine/data/example:visits", adminToken, null).statusCode());
        assertEquals(404, send("DELETE", "/players/Herobrine/data/example:visits", adminToken, null).statusCode());
        assertEquals(404, send("GET", "/players/Nobody", adminToken, null).statusCode());
        assertEquals(404, send("PUT", "/players/Nobody/data/x", adminToken, "{\"value\":1}").statusCode());
    }

    private static JsonObject value(List<JsonObject> values, String key) {
        return values.stream().filter(v -> v.get("key").getAsString().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void managesWrappers() throws Exception {
        var wrappers = json(send("GET", "/wrappers", adminToken, null)).getAsJsonArray();
        assertTrue(wrappers.size() >= 2);
        assertFalse(wrappers.toString().contains("\"key\""), "wrapper keys must never be listed");

        var created = send("POST", "/wrappers", adminToken, "{\"address\":\"10.0.0.50\"}");
        assertEquals(201, created.statusCode());
        var data = json(created).getAsJsonObject().getAsJsonObject("data");
        assertEquals(36, data.get("key").getAsString().length());
        assertEquals(400, send("POST", "/wrappers", adminToken, "{\"address\":\"x\",\"key\":\"nope\"}").statusCode());
        assertEquals(404, send("POST", "/wrappers/Wrapper-2/stop", adminToken, null).statusCode());
    }

    @Test
    void togglesMaintenanceAndManagesModules() throws Exception {
        var off = json(send("PUT", "/maintenance", adminToken, "{\"enabled\":false}")).getAsJsonObject();
        assertFalse(off.get("enabled").getAsBoolean());
        assertFalse(api.maintenance);
        assertFalse(json(send("GET", "/overview", adminToken, null)).getAsJsonObject().get("maintenance").getAsBoolean());

        var modules = json(send("GET", "/modules", adminToken, null)).getAsJsonObject();
        assertEquals("RestModule", modules.get("self").getAsString());
        assertEquals(409, send("POST", "/modules/RestModule/disable", adminToken, null).statusCode());
        assertEquals(200, send("POST", "/modules/load", adminToken, "{\"file\":\"StatsModule.jar\"}").statusCode());
        assertEquals(404, send("POST", "/modules/load", adminToken, "{\"file\":\"../../evil.jar\"}").statusCode());
        assertEquals(200, send("POST", "/modules/StatsModule/disable", adminToken, null).statusCode());
        assertEquals(400, send("GET", "/stats?limit=0", adminToken, null).statusCode());
        assertEquals(5, json(send("GET", "/stats?limit=5", adminToken, null)).getAsJsonArray().size());
    }

    @Test
    void managesUsersWithoutPrivilegeEscalation() throws Exception {
        var created = send("POST", "/users", adminToken,
                "{\"username\":\"moderator\",\"password\":\"moderator123\",\"permissions\":[\"SEE_ALL_USERS\",\"CHANGE_USER_PERMISSIONS\",\"CREATE_USER\"]}");
        assertEquals(201, created.statusCode(), created.body());
        assertEquals(409, send("POST", "/users", adminToken, "{\"username\":\"moderator\",\"password\":\"moderator123\"}").statusCode());
        assertEquals(400, send("POST", "/users", adminToken, "{\"username\":\"a/b\",\"password\":\"moderator123\"}").statusCode());
        assertEquals(400, send("POST", "/users", adminToken, "{\"username\":\"shortpw\",\"password\":\"123\"}").statusCode());

        var moderator = login("moderator", "moderator123");
        // The moderator cannot grant permissions they do not have
        var escalate = send("PUT", "/users/moderator/permissions", moderator,
                "{\"permissions\":[\"SEE_ALL_USERS\",\"CHANGE_USER_PERMISSIONS\",\"CREATE_USER\",\"STOP_SERVER\"]}");
        assertEquals(403, escalate.statusCode());
        var createAdmin = send("POST", "/users", moderator,
                "{\"username\":\"sneaky\",\"password\":\"sneaky1234\",\"permissions\":[\"MANAGE_MODULES\"]}");
        assertEquals(403, createAdmin.statusCode());
        // ... nor lock themselves out
        assertEquals(409, send("PUT", "/users/moderator/permissions", moderator, "{\"permissions\":[]}").statusCode());

        assertEquals(200, send("PUT", "/users/moderator/permissions", adminToken, "{\"permissions\":[\"SEE_ALL_USERS\"]}").statusCode());
        assertEquals(409, send("DELETE", "/users/tester", adminToken, null).statusCode());
        assertEquals(200, send("DELETE", "/users/moderator", adminToken, null).statusCode());
        assertEquals(401, send("GET", "/auth/me", moderator, null).statusCode(), "sessions of deleted users must end");
    }

    @Test
    void changesPasswordAndEndsOtherSessions() throws Exception {
        TestCloud.createUser("changer", "oldpassword");
        var first = login("changer", "oldpassword");
        var second = login("changer", "oldpassword");
        assertEquals(400, send("POST", "/auth/password", first,
                "{\"current_password\":\"wrong\",\"new_password\":\"newpassword\"}").statusCode());
        assertEquals(200, send("POST", "/auth/password", first,
                "{\"current_password\":\"oldpassword\",\"new_password\":\"newpassword\"}").statusCode());
        assertEquals(200, send("GET", "/auth/me", first, null).statusCode());
        assertEquals(401, send("GET", "/auth/me", second, null).statusCode());
        assertNotNull(login("changer", "newpassword"));
    }

    @Test
    void streamsConsoleOverWebSocket() throws Exception {
        var ticketResponse = json(send("POST", "/servers/Proxy-1/console/ticket", adminToken, null)).getAsJsonObject();
        var path = ticketResponse.get("path").getAsString();

        var messages = new LinkedBlockingQueue<JsonObject>();
        var socket = http.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + path), new CollectingListener(messages))
                .get(5, TimeUnit.SECONDS);

        var history = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(history);
        assertEquals("history", history.get("type").getAsString());
        assertTrue(history.get("can_execute").getAsBoolean());

        api.servers.get("Proxy-1").console.add("live line");
        var live = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(live);
        assertEquals("live line", live.get("line").getAsString());

        socket.sendText("{\"type\":\"command\",\"command\":\"glist\"}", true).get(5, TimeUnit.SECONDS);
        var echoed = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(echoed);
        assertEquals("> glist", echoed.get("line").getAsString());
        assertTrue(api.executedCommands.contains("Proxy-1:glist"));
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);

        // Tickets are single-use
        var reuse = new CompletableFuture<Integer>();
        http.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + port + path), new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                reuse.complete(statusCode);
                return null;
            }
        });
        assertEquals(4401, reuse.get(5, TimeUnit.SECONDS));
    }

    private static String login(String username, String password) throws Exception {
        var response = send("POST", "/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertEquals(200, response.statusCode(), response.body());
        return json(response).getAsJsonObject().get("token").getAsString();
    }

    @Test
    void serversCanBeSavedIntoATemplate() throws Exception {
        var server = json(send("GET", "/servers", adminToken, null)).getAsJsonArray().get(0).getAsJsonObject()
                .get("server_name").getAsString();

        var response = send("POST", "/servers/" + server + "/save-template", adminToken, "{\"variant\":\"map-desert\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(api.savedTemplates.contains(server + ":map-desert"));

        send("POST", "/servers/" + server + "/save-template", adminToken, null);
        assertTrue(api.savedTemplates.contains(server + ":default"), "default without a variant");

        var viewer = login("viewer", "viewerpassword");
        assertEquals(403, send("POST", "/servers/" + server + "/save-template", viewer, null).statusCode());
    }

    @Test
    void templateFilesCanBeUploadedListedDownloadedAndDeleted() throws Exception {
        var upload = HttpRequest.newBuilder(URI.create(base + "/templates/Lobby/default/file?path=plugins/config.yml"))
                .header("Authorization", "Bearer " + adminToken)
                .PUT(HttpRequest.BodyPublishers.ofString("motd: hello")).build();
        assertEquals(200, http.send(upload, HttpResponse.BodyHandlers.ofString()).statusCode());

        var list = json(send("GET", "/templates/Lobby/default/files?path=plugins", adminToken, null)).getAsJsonArray();
        assertEquals("config.yml", list.get(0).getAsJsonObject().get("name").getAsString());

        var download = send("GET", "/templates/Lobby/default/file?path=plugins/config.yml", adminToken, null);
        assertEquals(200, download.statusCode());
        assertEquals("motd: hello", download.body());

        assertEquals(200, send("DELETE", "/templates/Lobby/default/file?path=plugins/config.yml", adminToken, null).statusCode());
        assertEquals(404, send("GET", "/templates/Lobby/default/file?path=plugins/config.yml", adminToken, null).statusCode());
        assertEquals(400, send("GET", "/templates/Lobby/default/file", adminToken, null).statusCode(), "no path");

        var viewer = login("viewer", "viewerpassword");
        assertEquals(403, send("GET", "/templates", viewer, null).statusCode());
    }

    @Test
    void metricsNeedTheirOwnToken() throws Exception {
        assertEquals(401, send("GET", "/metrics", null, null).statusCode());
        assertEquals(401, send("GET", "/metrics", adminToken, null).statusCode(), "a login is not the metrics token");

        var response = send("GET", "/metrics", TestCloud.METRICS_TOKEN, null);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"));
        var body = response.body();
        assertTrue(body.contains("# TYPE rexlcloud_players_online gauge"), body);
        assertTrue(body.matches("(?s).*\\nrexlcloud_players_online \\d+\\n.*"), body);
        assertTrue(body.matches("(?s).*\\nrexlcloud_servers\\{group=\"[^\"]+\",state=\"[a-z]+\"} \\d+\\n.*"), body);
        assertTrue(body.contains("rexlcloud_wrapper_connected{wrapper="), body);
    }

    private static HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonElement json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body());
    }

    private static final class CollectingListener implements WebSocket.Listener {
        private final LinkedBlockingQueue<JsonObject> messages;
        private final StringBuilder buffer = new StringBuilder();

        private CollectingListener(LinkedBlockingQueue<JsonObject> messages) {
            this.messages = messages;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                messages.add(JsonParser.parseString(buffer.toString()).getAsJsonObject());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
    }
}
