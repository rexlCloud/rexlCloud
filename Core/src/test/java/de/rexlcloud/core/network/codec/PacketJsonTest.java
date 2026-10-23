package de.rexlcloud.core.network.codec;

import com.google.gson.JsonParseException;
import de.rexlcloud.core.api.CloudOfflinePlayer;
import de.rexlcloud.core.api.PlayerDatabaseData;
import de.rexlcloud.core.api.Server;
import de.rexlcloud.core.api.ServerLocation;
import de.rexlcloud.core.api.Wrapper;
import de.rexlcloud.core.api.exceptions.PlayerNotFoundException;
import de.rexlcloud.core.api.exceptions.ServerNotFoundException;
import de.rexlcloud.core.config.MessageKey;
import de.rexlcloud.core.config.PluginMessageConfig;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerStartPacket;
import de.rexlcloud.core.network.packets.TemplateFilePacket;
import de.rexlcloud.core.network.packets.TemplateStatePacket;
import de.rexlcloud.core.network.packets.WrapperInfoPacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacketToSpecific;
import de.rexlcloud.core.network.packets.api.ApiResponsePacket;
import de.rexlcloud.core.network.packets.api.ApiUpdateObjectPacket;
import de.rexlcloud.core.server.ProxyForwarding;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.core.server.runtime.JavaRuntime;
import de.rexlcloud.core.server.template.TemplateFile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PacketJsonTest {

    private static final ServerGroup GROUP = new ServerGroup("Lobby", ServerType.SERVER, 1024, false, null, 1,
            true, false, "-XX:+UseG1GC", 50, "/opt/java/jdk-25/bin/java");
    private static final UUID KEY = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    /**
     * Sends a container through the real pipeline (frame + JSON) and returns what the other side receives.
     */
    private static PacketContainer overTheWire(PacketContainer container) {
        var sender = new EmbeddedChannel();
        PacketPipeline.configure(sender.pipeline());
        assertTrue(sender.writeOutbound(container));
        var receiver = new EmbeddedChannel();
        PacketPipeline.configure(receiver.pipeline());
        ByteBuf bytes;
        while ((bytes = sender.readOutbound()) != null) {
            receiver.writeInbound(bytes);
        }
        PacketContainer received = receiver.readInbound();
        assertNotNull(received, "nothing arrived");
        return received;
    }

    @Test
    void aCustomJarGroupStaysCustomOverTheWireAndInGroupsJson() {
        var group = new ServerGroup("Game", ServerType.SERVER, 1024, false, null, 1, false, false, "", 50, "java");
        group.setCustomJar(true);
        var packet = new ServerStartPacket("Game-1", group, 25700, KEY, false,
                new ProxyForwarding(ProxyForwarding.Mode.MODERN, "s3cret"));

        var received = (ServerStartPacket) overTheWire(new PacketContainer(PacketType.SERVER_START, packet)).getPacket();
        assertTrue(received.getServerGroup().isCustomJar());

        var json = de.rexlcloud.core.config.JsonConfiguration.GSON().toJson(group);
        var loaded = de.rexlcloud.core.config.JsonConfiguration.GSON().fromJson(json, ServerGroup.class);
        assertTrue(loaded.isCustomJar());
        // Groups saved before custom jars existed
        var old = json.replace("\"customJar\": true", "\"customJar\": false");
        assertFalse(de.rexlcloud.core.config.JsonConfiguration.GSON().fromJson(old, ServerGroup.class).isCustomJar());
    }

    @Test
    void theWrapperReportsItsRunningServers() {
        var running = new de.rexlcloud.core.network.packets.WrapperConnectPacket.RunningServer("Lobby-1", KEY, "Lobby",
                25700, true);
        var packet = new de.rexlcloud.core.network.packets.WrapperConnectPacket(KEY, java.util.List.of(running));

        var received = (de.rexlcloud.core.network.packets.WrapperConnectPacket)
                overTheWire(new PacketContainer(PacketType.WRAPPER_CONNECT, packet)).getPacket();

        assertEquals(KEY, received.getAuthKey());
        var server = received.getServers().getFirst();
        assertEquals("Lobby-1", server.getName());
        assertEquals(KEY, server.getKey());
        assertEquals("Lobby", server.getGroup());
        assertEquals(25700, server.getPort());
        assertTrue(server.isOnline());
    }

    @Test
    void serverStartPacketKeepsItsGroupAndForwarding() {
        var packet = new ServerStartPacket("Lobby-1", GROUP, 25700, KEY, true,
                new ProxyForwarding(ProxyForwarding.Mode.MODERN, "s3cret"));

        var received = overTheWire(new PacketContainer(PacketType.SERVER_START, packet));

        assertEquals(PacketType.SERVER_START, received.getPacketType());
        var start = (ServerStartPacket) received.getPacket();
        assertEquals("Lobby-1", start.getName());
        assertEquals(25700, start.getPort());
        assertEquals(KEY, start.getKey());
        assertTrue(start.isMaintenance());
        assertEquals("/opt/java/jdk-25/bin/java", start.getServerGroup().getJavaCommand());
        assertEquals("-XX:+UseG1GC", start.getServerGroup().getJavaArguments());
        assertEquals(ProxyForwarding.Mode.MODERN, start.getForwarding().mode());
        assertEquals("s3cret", start.getForwarding().secret());
    }

    @Test
    void templateChunksStayCompact() {
        var data = new byte[1024 * 1024];
        new java.util.Random(7).nextBytes(data);
        var packet = new TemplateFilePacket("Lobby", "default/server.jar", data.length, "abc", 0, 1, data);

        var json = PacketJson.write(new PacketContainer(PacketType.SERVER_TEMPLATE, packet));
        var received = (TemplateFilePacket) overTheWire(new PacketContainer(PacketType.SERVER_TEMPLATE, packet)).getPacket();

        assertTrue(json.length() < 1_500_000, "Base64 instead of a number array: " + json.length());
        assertArrayEquals(data, received.getData());
    }

    @Test
    void nestedGenericFieldsSurvive() {
        var state = new TemplateStatePacket(Map.of("Lobby", Map.of("default/server.jar", new TemplateFile(42, "ff"))));
        var info = new WrapperInfoPacket(0.5, 1, 2, 3, List.of(new JavaRuntime("/opt/java/bin/java", "25.0.4", 25, "Eclipse Adoptium")));

        var receivedState = (TemplateStatePacket) overTheWire(new PacketContainer(PacketType.TEMPLATE_STATE, state)).getPacket();
        var receivedInfo = (WrapperInfoPacket) overTheWire(new PacketContainer(PacketType.WRAPPER_INFO, info)).getPacket();

        assertEquals(new TemplateFile(42, "ff"), receivedState.getTemplates().get("Lobby").get("default/server.jar"));
        assertEquals("Eclipse Adoptium", receivedInfo.getJavaRuntimes().getFirst().vendor());
        assertEquals(0.5, receivedInfo.getCpuUsage());
    }

    @Test
    void notANumberStaysNotANumber() {
        var info = new WrapperInfoPacket(Double.NaN, 1, 2, 3, List.of());
        var received = (WrapperInfoPacket) overTheWire(new PacketContainer(PacketType.WRAPPER_INFO, info)).getPacket();
        assertTrue(Double.isNaN(received.getCpuUsage()));
    }

    @Test
    void plainValuesAndMessagesAsPayload() {
        assertEquals("Lobby-1", overTheWire(new PacketContainer(PacketType.SERVER_STOP, "Lobby-1")).getPacket());
        assertNull(overTheWire(new PacketContainer(PacketType.WRAPPER_STOP, null)).getPacket());

        var messages = new PluginMessageConfig();
        messages.loadDefault();
        var received = (PluginMessageConfig) overTheWire(new PacketContainer(PacketType.SYNC_MESSAGES, messages)).getPacket();
        assertEquals(messages.messages(), received.messages());
        assertEquals(MessageKey.values().length, received.messages().size());
    }

    @Test
    void apiRequestArgumentsKeepTheirTypes() {
        var uuid = UUID.randomUUID();
        Object[] mixed = {uuid, "coins", new byte[]{1, 2, 3}, 5, null};
        var request = new ApiRequestPacket<>(7, "Lobby-1", mixed);
        var strings = new ApiRequestPacket<>(8, "Lobby-1", new String[]{"Lobby", "2"});

        var receivedMixed = (ApiRequestPacket<?>) overTheWire(new PacketContainer(PacketType.API_REQUEST_PLAYER_DATA_UPDATE, request)).getPacket();
        var receivedStrings = (ApiRequestPacket<?>) overTheWire(new PacketContainer(PacketType.API_REQUEST_START_SERVER, strings)).getPacket();

        assertEquals(7, receivedMixed.getRequestId());
        assertEquals(uuid, receivedMixed.getData()[0]);
        assertEquals("coins", receivedMixed.getData()[1]);
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) receivedMixed.getData()[2]);
        assertEquals(5, receivedMixed.getData()[3]);
        assertNull(receivedMixed.getData()[4]);
        // Handlers cast to String[], so the array type has to stay
        assertInstanceOf(String[].class, receivedStrings.getData());
        String[] data = (String[]) receivedStrings.getData();
        assertArrayEquals(new String[]{"Lobby", "2"}, data);
    }

    @Test
    void requestsToASpecificServerKeepTheirTarget() {
        var request = new ApiRequestPacketToSpecific<>(9, "Proxy-1", new String[]{"x"}, "Lobby-1");
        var received = overTheWire(new PacketContainer(PacketType.API_REQUEST_GET_PLAYER_LOCATION, request)).getPacket();
        var specific = assertInstanceOf(ApiRequestPacketToSpecific.class, received);
        assertEquals("Lobby-1", specific.getTargetServerName());
        assertEquals(9, specific.getRequestId());
    }

    @Test
    void apiObjectsAndListsAsResponses() {
        var wrapper = new Wrapper("Wrapper-1", true, 0.25, 1, 2, 3);
        var server = new Server("Lobby-1", KEY, GROUP, 25700, wrapper, "", true);
        var servers = new ArrayList<Server>(List.of(server));

        var single = (ApiResponsePacket) overTheWire(new PacketContainer(PacketType.API_REQUEST_GET_SERVERS,
                new ApiResponsePacket(1, "Proxy-1", server))).getPacket();
        var list = (ApiResponsePacket) overTheWire(new PacketContainer(PacketType.API_REQUEST_GET_SERVERS,
                new ApiResponsePacket(2, "Proxy-1", servers))).getPacket();
        var location = (ApiResponsePacket) overTheWire(new PacketContainer(PacketType.API_REQUEST_GET_PLAYER_LOCATION,
                new ApiResponsePacket(3, "Proxy-1", new ServerLocation("world", 1, 64, 2)))).getPacket();

        var receivedServer = assertInstanceOf(Server.class, single.getResponse());
        assertEquals("Lobby-1", receivedServer.getName());
        assertEquals("Wrapper-1", receivedServer.getWrapper().getName());
        assertEquals("Lobby", receivedServer.getServerGroup().getName());
        assertEquals("Lobby-1", ((Server) ((List<?>) list.getResponse()).getFirst()).getName());
        assertEquals("world", ((ServerLocation) location.getResponse()).getWorldName());
    }

    @Test
    void offlinePlayersArriveWithTheirData() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.ObjectOutputStream(bytes)) {
            out.writeObject(250);
        }
        var data = new ArrayList<>(List.of(new PlayerDatabaseData(KEY, "coins", bytes.toByteArray())));
        var player = new CloudOfflinePlayer("Notch", KEY, 1_000L, 2_000L, false, data);

        var received = (ApiResponsePacket) overTheWire(new PacketContainer(PacketType.API_REQUEST_GET_OFFLINE_PLAYER,
                new ApiResponsePacket(1, "Lobby-1", player))).getPacket();

        var offline = assertInstanceOf(CloudOfflinePlayer.class, received.getResponse());
        assertEquals("Notch", offline.getName());
        assertEquals(KEY, offline.getUuid());
        assertEquals(1_000L, offline.getFirstLogin());
        assertEquals(2_000L, offline.getLastLogin());
        assertFalse(offline.isOnline());
        assertEquals(List.of("coins"), offline.getDataKeys());
        assertEquals(250, offline.<Integer>getData("coins").orElseThrow());
        assertTrue(offline.getData("missing").isEmpty());
    }

    @Test
    void exceptionsKeepTypeAndMessage() {
        var error = new ServerNotFoundException("Lobby-9");
        var received = (ApiResponsePacket) overTheWire(new PacketContainer(PacketType.API_REQUEST_GET_SERVERS,
                new ApiResponsePacket(1, "Proxy-1", error))).getPacket();

        var exception = assertInstanceOf(ServerNotFoundException.class, received.getResponse());
        assertEquals(error.getMessage(), exception.getMessage());
        assertInstanceOf(PlayerNotFoundException.class, PacketJson.roundTrip(new PlayerNotFoundException("Notch")));
        assertInstanceOf(RuntimeException.class, PacketJson.roundTrip(new IllegalStateException("plain")));
    }

    @Test
    void updatesOfObjects() {
        var wrapper = new Wrapper("Wrapper-1", true, 0.25, 1, 2, 3);
        var received = (ApiUpdateObjectPacket) overTheWire(new PacketContainer(PacketType.API_UPDATE_WRAPPER_OBJECT,
                new ApiUpdateObjectPacket("Lobby-1", "Wrapper-1", wrapper))).getPacket();
        assertEquals("Lobby-1", received.getTargetServer());
        assertEquals(0.25, ((Wrapper) received.getObject()).getCpuUsage());
    }

    @Test
    void foreignClassesAreRefused() {
        var processBuilder = """
                {"type":"SERVER_STOP","packet":{"t":"obj","c":"java.lang.ProcessBuilder","v":{"command":["touch","/tmp/x"]}}}""";
        var unknownType = """
                {"type":"NOT_A_TYPE","packet":null}""";
        var foreignArray = """
                {"type":"SERVER_STOP","packet":{"t":"array","c":"java.lang.ProcessBuilder","v":[]}}""";

        assertThrows(JsonParseException.class, () -> PacketJson.read(processBuilder));
        assertThrows(JsonParseException.class, () -> PacketJson.read(unknownType));
        assertThrows(JsonParseException.class, () -> PacketJson.read(foreignArray));
        assertThrows(JsonParseException.class, () -> PacketJson.write(new PacketContainer(PacketType.SERVER_STOP, new StringBuilder("x"))));
    }

    @Test
    void invalidAndOversizedFramesAreHandled() {
        var receiver = new EmbeddedChannel();
        PacketPipeline.configure(receiver.pipeline());

        // Garbage is dropped and the connection keeps working
        var garbage = "not json".getBytes(StandardCharsets.UTF_8);
        receiver.writeInbound(Unpooled.buffer().writeInt(garbage.length).writeBytes(garbage));
        assertNull(receiver.readInbound());

        var json = PacketJson.write(new PacketContainer(PacketType.SERVER_STOP, "Lobby-1")).getBytes(StandardCharsets.UTF_8);
        receiver.writeInbound(Unpooled.buffer().writeInt(json.length).writeBytes(json));
        assertEquals("Lobby-1", ((PacketContainer) receiver.readInbound()).getPacket());

        // A frame announcing more than the limit is refused before anything is buffered, and the connection closed
        var oversized = new EmbeddedChannel();
        PacketPipeline.configure(oversized.pipeline());
        oversized.writeInbound(Unpooled.buffer().writeInt(PacketPipeline.MAX_FRAME_BYTES + 1).writeBytes(new byte[16]));
        assertFalse(oversized.isOpen());
    }


}
