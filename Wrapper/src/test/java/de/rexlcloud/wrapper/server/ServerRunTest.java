package de.rexlcloud.wrapper.server;

import de.rexlcloud.core.server.ProxyForwarding;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.ServerType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServerRunTest {

    private static Server server() {
        var group = new ServerGroup("Lobby", ServerType.SERVER, 1024, false, null, 1, true, false, "", 50, "java");
        return new Server("Lobby-1", group, 25700, UUID.randomUUID(), false,
                new ProxyForwarding(ProxyForwarding.Mode.MODERN, "secret"));
    }

    @Test
    void onlyTheTokenOfThisRunConnects() {
        var server = server();
        var token = server.getConnectToken();

        assertTrue(token.length() >= 40, "32 random bytes: " + token);
        assertTrue(server.checkConnectToken(token));
        assertFalse(server.checkConnectToken(token.substring(1)));
        assertFalse(server.checkConnectToken(""));
        assertFalse(server.checkConnectToken(null));
        assertFalse(server.checkConnectToken(server().getConnectToken()), "another run's token");
    }

    @Test
    void aServerThatStopsItselfIsNotACrash() {
        assertTrue(Server.isCleanExit(true, 0), "\"stop\" in the console");
        assertFalse(Server.isCleanExit(true, 1), "error exit while online");
        assertFalse(Server.isCleanExit(false, 0), "exited before it came online, e.g. the plugin could not connect");
        assertFalse(Server.isCleanExit(false, 137), "killed while starting");
    }

}
