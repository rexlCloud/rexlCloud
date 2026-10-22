package de.rexlcloud.core.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static de.rexlcloud.core.server.ServerLifecycle.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerLifecycleTest {

    /**
     * A server that only records what the manager asks of it.
     */
    static final class TestServer extends AbstractServer {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();

        TestServer(String name, ServerGroup group, int port) {
            super(name, group, port, UUID.randomUUID());
        }

        @Override
        public void start() {
            if (moveTo(STARTING)) {
                starts.incrementAndGet();
                setKey(UUID.randomUUID());
            }
        }

        @Override
        public CompletableFuture<AbstractServer> stop() {
            if (moveTo(STOPPING)) {
                stops.incrementAndGet();
            }
            return CompletableFuture.completedFuture(this);
        }

        void age(long millis) throws ReflectiveOperationException {
            var since = AbstractServer.class.getDeclaredField("lifecycleSince");
            since.setAccessible(true);
            since.setLong(this, System.currentTimeMillis() - millis);
        }
    }

    private static ServerGroup group(int minServers) {
        return new ServerGroup("Lobby", ServerType.SERVER, 1024, false, null, minServers, true, false, "", 50, "java");
    }

    private static TestServer online(ServerManager<TestServer> manager, ServerGroup group, String name, int port) {
        var server = new TestServer(name, group, port);
        manager.startServer(server);
        server.moveTo(ONLINE);
        return server;
    }

    @Test
    void onlyAllowedTransitions() {
        var server = new TestServer("Lobby-1", group(1), 25700);
        assertFalse(server.moveTo(ONLINE), "not online before it started");
        assertTrue(server.moveTo(STARTING));
        assertTrue(server.isStarted());
        assertTrue(server.moveTo(ONLINE));
        assertFalse(server.moveTo(STARTING), "no second start while running");
        assertTrue(server.moveTo(STOPPING));
        assertFalse(server.isStarted());
        assertTrue(server.moveTo(STOPPED));
        for (var next : ServerLifecycle.values()) {
            assertFalse(server.moveTo(next), "stopped for good, not " + next);
        }
    }

    @Test
    void aServerTheMinimumNeedsIsStartedAgain() {
        var manager = new ServerManager<TestServer>(false);
        var group = group(1);
        var server = online(manager, group, "Lobby-1", 25700);

        manager.stopServer(server);
        assertEquals(STOPPING, server.getLifecycle());
        // The wrapper confirms the stop
        manager.finishStop(server);
        assertEquals(WAITING, server.getLifecycle());
        assertTrue(manager.getServers().contains(server));

        manager.tick();
        assertEquals(STARTING, server.getLifecycle());
        assertEquals(2, server.starts.get());
    }

    @Test
    void aServerAboveTheMinimumIsRemoved() {
        var manager = new ServerManager<TestServer>(false);
        var group = group(1);
        online(manager, group, "Lobby-1", 25700);
        var extra = online(manager, group, "Lobby-2", 25701);

        manager.stopServer(extra);
        manager.finishStop(extra);

        assertEquals(STOPPED, extra.getLifecycle());
        assertFalse(manager.getServers().contains(extra));
    }

    @Test
    void serversOfDeletedGroupsAreNeverStartedAgain() {
        var manager = new ServerManager<TestServer>(false);
        var group = group(1);
        var server = online(manager, group, "Lobby-1", 25700);
        manager.setGroupExists(g -> false);

        manager.stopServersFromGroup(group);
        manager.finishStop(server);
        manager.tick();

        assertEquals(STOPPED, server.getLifecycle());
        assertTrue(manager.getServers().isEmpty());
        assertEquals(1, server.starts.get());
    }

    @Test
    void aReportAboutAnEarlierRunDoesNotFindTheCurrentOne() {
        var manager = new ServerManager<TestServer>(false);
        var server = online(manager, group(1), "Lobby-1", 25700);
        var firstRun = server.getKey();
        manager.stopServer(server);
        manager.finishStop(server);
        manager.tick();

        assertNotEquals(firstRun, server.getKey(), "every start gets a new key");
        assertTrue(manager.getServerByNameAndKey("Lobby-1", firstRun).isEmpty());
        assertSame(server, manager.getServerByNameAndKey("Lobby-1", server.getKey()).orElseThrow());
    }

    @Test
    void namesFindTheCurrentRunAndPortsOfStoppingServersStayTaken() {
        var manager = new ServerManager<TestServer>(false);
        var group = group(0);
        var old = online(manager, group, "Lobby-1", 25700);
        manager.stopServer(old);

        // Still stopping: its name and port are not given to a new server
        assertEquals("Lobby-2", manager.getServerNameForNextServer(group));
        assertEquals(25701, manager.getServerPort(group));
        assertEquals(0, manager.getServersAmountFromGroup(group), "stopping servers do not count");

        var current = new TestServer("Lobby-1", group, 25702);
        manager.startServer(current);
        assertSame(current, manager.getServerByName("Lobby-1").orElseThrow());
    }

    @Test
    void hangingStartsAndStopsAreResolved() throws ReflectiveOperationException {
        var manager = new ServerManager<TestServer>(false);
        var group = group(1);
        var starting = new TestServer("Lobby-1", group, 25700);
        manager.startServer(starting);
        starting.age(ServerManager.START_TIMEOUT_MILLIS + 1);

        manager.tick();
        assertEquals(STOPPING, starting.getLifecycle(), "never connected: stopped");

        starting.age(ServerManager.STOP_TIMEOUT_MILLIS + 1);
        manager.tick();
        assertEquals(WAITING, starting.getLifecycle(), "stop never confirmed: completed, and waiting for the minimum");
    }

    @Test
    void waitingServersThatAreNotNeededAreRemovedRightAway() {
        var manager = new ServerManager<TestServer>(false);
        var group = group(0);
        var waiting = new TestServer("Lobby-1", group, 25700);
        manager.getServers().add(waiting);

        manager.stopServer(waiting);

        assertEquals(STOPPED, waiting.getLifecycle());
        assertTrue(manager.getServers().isEmpty());
        assertEquals(0, waiting.stops.get());
    }

}
