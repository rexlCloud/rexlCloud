package de.rexlcloud.bridge.helper;

import de.rexlcloud.core.api.ServerLocation;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import java.util.UUID;

/**
 * Converts between Minestom positions and the cloud's {@link ServerLocation}. The location's world name is the UUID of
 * the instance.
 */
public final class RexlCloudHelper {

    private RexlCloudHelper() {
    }

    public static ServerLocation posToServerLocation(Instance instance, Pos pos) {
        return posToServerLocation(instance, pos, true);
    }

    public static ServerLocation posToServerLocation(Instance instance, Pos pos, boolean withRotation) {
        var world = instance == null ? null : instance.getUuid().toString();
        return withRotation
                ? new ServerLocation(world, pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch())
                : new ServerLocation(world, pos.x(), pos.y(), pos.z());
    }

    public static ServerLocation posToServerLocation(Pos pos) {
        return posToServerLocation(null, pos, true);
    }

    public static ServerLocation posToServerLocation(Pos pos, boolean withRotation) {
        return posToServerLocation(null, pos, withRotation);
    }

    public static Pos serverLocationToPos(ServerLocation serverLocation) {
        return new Pos(serverLocation.getX(), serverLocation.getY(), serverLocation.getZ(), serverLocation.getYaw(),
                serverLocation.getPitch());
    }

    /**
     * Moves the player to the location on the next tick: into the location's instance if it names one that exists,
     * otherwise within the player's current instance.
     */
    public static void teleport(Player player, ServerLocation location) {
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            var pos = serverLocationToPos(location);
            var instance = instanceOf(location);
            if (instance != null && instance != player.getInstance()) {
                player.setInstance(instance, pos);
            } else {
                player.teleport(pos);
            }
        });
    }

    private static Instance instanceOf(ServerLocation location) {
        var world = location.getWorldName();
        if (world == null) {
            return null;
        }
        try {
            return MinecraftServer.getInstanceManager().getInstance(UUID.fromString(world));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
