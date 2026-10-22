package de.rexlcloud.bridge;

import net.minestom.server.Auth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RexlCloudBridgeTest {

    @TempDir
    Path folder;

    private RexlCloudBridge bridge(String cloudJson) throws Exception {
        var file = folder.resolve("cloud.json");
        Files.writeString(file, cloudJson);
        return new RexlCloudBridge(file);
    }

    @Test
    void readsTheServerFromCloudJson() throws Exception {
        var key = UUID.randomUUID();
        var bridge = bridge("{\"ServerName\":\"Game-1\",\"ServerPort\":25702,\"ServerKey\":\"" + key + "\","
                + "\"Maintenance\":true,\"WrapperPort\":12243,\"WrapperToken\":\"abc\",\"TemplateVariant\":\"desert\"}");

        assertTrue(bridge.isCloudServer());
        assertEquals("Game-1", bridge.getServerName());
        assertEquals(key, bridge.getServerKey());
        assertEquals(25702, bridge.getPort());
        assertEquals(12243, bridge.getWrapperPort());
        assertEquals("abc", bridge.getWrapperToken());
        assertEquals("desert", bridge.getTemplateVariant());
        assertTrue(bridge.isMaintenance());
    }

    @Test
    void forwardingMatchesTheProxies() throws Exception {
        var modern = bridge("{\"ForwardingMode\":\"MODERN\",\"ForwardingSecret\":\"s3cret\"}").getAuth();
        assertInstanceOf(Auth.Velocity.class, modern);
        assertInstanceOf(Auth.Bungee.class, bridge("{\"ForwardingMode\":\"LEGACY\",\"ForwardingSecret\":\"\"}").getAuth());
        assertInstanceOf(Auth.Offline.class, bridge("{}").getAuth());
    }

    @Test
    void runsWithoutTheCloud() {
        var bridge = new RexlCloudBridge(folder.resolve("missing.json"));

        assertFalse(bridge.isCloudServer());
        assertEquals(25565, bridge.getPort());
        assertNull(bridge.getServerName());
        assertInstanceOf(Auth.Offline.class, bridge.getAuth());
        // Does nothing without a cloud.json, in particular it does not exit
        bridge.initialize();
    }

}
