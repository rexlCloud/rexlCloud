package de.rexlcloud.example;

import de.rexlcloud.bridge.RexlCloudBridge;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.block.Block;

/**
 * A flat world on the port the cloud assigned, with the network's player forwarding.
 */
public final class ExampleServer {

    private ExampleServer() {
    }

    public static void main(String[] args) {
        var cloud = RexlCloudBridge.getInstance();
        var server = MinecraftServer.init(cloud.getAuth());

        var world = MinecraftServer.getInstanceManager().createInstanceContainer();
        world.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(world);
            event.getPlayer().setRespawnPoint(new Pos(0, 41, 0));
        });

        cloud.initialize();
        server.start("0.0.0.0", cloud.getPort());
    }

}
