package de.rexlcloud.master.server;

import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerStopPacket;
import de.rexlcloud.core.network.packets.WrapperConnectPacket.RunningServer;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.ServerLifecycle;
import de.rexlcloud.core.server.ServerManager;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.master.wrapper.Wrapper;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServerAdoptionTest {

    private final ServerGroup lobby = new ServerGroup("Lobby", ServerType.SERVER, 1024, false, null, 1, true, false,
            "", 50, "java");
    private final ServerManager<Server> serverManager = new ServerManager<>(false);
    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final Wrapper wrapper = new Wrapper(channel, "Wrapper-1", "127.0.0.1");

    private int adopt(RunningServer... running) {
        return ServerAdoption.adopt(wrapper, List.of(running), serverManager,
                name -> name.equals("Lobby") ? Optional.of(lobby) : Optional.empty());
    }

    private Server waiting(String name) {
        var server = new Server(name, lobby, 25700, UUID.randomUUID());
        serverManager.getServers().add(server);
        return server;
    }

    private List<ServerStopPacket> stopsSentToTheWrapper() {
        return channel.outboundMessages().stream()
                .map(PacketContainer.class::cast)
                .filter(container -> container.getPacketType() == PacketType.SERVER_STOP)
                .map(container -> (ServerStopPacket) container.getPacket())
                .toList();
    }

    @Test
    void afterAMasterRestartTheRunningServerTakesThePlaceOfTheWaitingOne() {
        var placeholder = waiting("Lobby-1");
        var key = UUID.randomUUID();

        assertEquals(1, adopt(new RunningServer("Lobby-1", key, "Lobby", 25701, true)));

        assertEquals(ServerLifecycle.STOPPED, placeholder.getLifecycle());
        var server = serverManager.getServerByNameAndKey("Lobby-1", key).orElseThrow();
        assertSame(wrapper, server.getWrapper());
        assertEquals(25701, server.getPort());
        // ONLINE follows with the wrapper's server info
        assertEquals(ServerLifecycle.STARTING, server.getLifecycle());
        assertEquals(1, serverManager.getServers().size());
        assertTrue(stopsSentToTheWrapper().isEmpty());
    }

    @Test
    void aServerWithAnotherNameAlsoReplacesAWaitingOneOfItsGroup() {
        waiting("Lobby-1");

        adopt(new RunningServer("Lobby-2", UUID.randomUUID(), "Lobby", 25701, true));

        assertEquals(List.of("Lobby-2"), serverManager.getServers().stream().map(Server::getName).toList(),
                "the group keeps its minimum of one, not two");
    }

    @Test
    void serversOfDeletedGroupsAreStopped() {
        var key = UUID.randomUUID();

        assertEquals(0, adopt(new RunningServer("Old-1", key, "Old", 25702, true)));

        assertTrue(serverManager.getServers().isEmpty());
        var stop = stopsSentToTheWrapper().getFirst();
        assertEquals("Old-1", stop.getName());
        assertEquals(key, stop.getKey());
    }

    @Test
    void aSecondServerOfTheSameNameIsStoppedNotTakenOver() {
        var running = waiting("Lobby-1");
        running.moveTo(ServerLifecycle.STARTING);

        assertEquals(0, adopt(new RunningServer("Lobby-1", UUID.randomUUID(), "Lobby", 25703, true)));

        assertEquals(List.of(running), serverManager.getServers());
        assertEquals(1, stopsSentToTheWrapper().size());
    }

    @Test
    void afterAConnectionDropTheServersMoveToTheNewConnection() {
        var oldConnection = new Wrapper(new EmbeddedChannel(), "Wrapper-1", "127.0.0.1");
        var server = waiting("Lobby-1");
        server.moveTo(ServerLifecycle.STARTING);
        server.setWrapper(oldConnection);

        assertEquals(0, adopt(new RunningServer("Lobby-1", server.getKey(), "Lobby", 25700, true)),
                "nothing new to take over");

        assertSame(wrapper, server.getWrapper());
        assertEquals(ServerLifecycle.STARTING, server.getLifecycle());
        assertTrue(stopsSentToTheWrapper().isEmpty());
    }

}
