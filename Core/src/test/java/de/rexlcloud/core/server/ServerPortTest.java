package de.rexlcloud.core.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerPortTest {

    private static ServerGroup group(ServerType type) {
        return new ServerGroup("Test", type, 1024, false, null, 1, false, false, "", 100, "java");
    }

    @Test
    void blockedPortsAreSkipped() {
        var manager = new ServerManager<AbstractServer>(false);
        assertEquals(25565, manager.getServerPort(group(ServerType.PROXY)));
        assertEquals(25700, manager.getServerPort(group(ServerType.SERVER)));

        manager.blockPort(25565);
        manager.blockPort(25566);
        manager.blockPort(25700);

        assertEquals(25567, manager.getServerPort(group(ServerType.PROXY)));
        assertEquals(25701, manager.getServerPort(group(ServerType.SERVER)));
    }

}
