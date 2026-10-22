package de.rexlcloud.wrapper.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortCheckTest {

    @Test
    void aPortInUseIsNotFree() throws IOException {
        int port;
        try (var other = new ServerSocket()) {
            other.bind(new InetSocketAddress(0));
            port = other.getLocalPort();
            assertFalse(Server.isPortFree(port));
        }
        assertTrue(Server.isPortFree(port));
    }

}
