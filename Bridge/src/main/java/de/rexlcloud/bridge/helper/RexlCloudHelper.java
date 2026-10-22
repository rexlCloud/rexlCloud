package de.rexlcloud.bridge.helper;

import de.rexlcloud.core.api.ServerLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * Converts between Bukkit {@link Location}s and the cloud's {@link ServerLocation} (the world is stored by its
 * name).
 */
public class RexlCloudHelper {

    /**
     * @return the location with its world and viewing direction
     */
    public static ServerLocation locationToServerLocation(Location location) {
        return locationToServerLocation(location, true);
    }

    /**
     * @param withRotation whether the viewing direction is kept
     * @return the location with its world
     */
    public static ServerLocation locationToServerLocation(Location location, boolean withRotation) {
        if (withRotation) {
            return new ServerLocation(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        } else {
            return new ServerLocation(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
        }
    }

    /**
     * @param world the world to store instead of the location's, {@code null} for none
     * @return the location with the viewing direction
     */
    public static ServerLocation locationToServerLocation(@Nullable World world, Location location) {
        return locationToServerLocation(world, location, true);
    }

    /**
     * @param world        the world to store instead of the location's, {@code null} for none
     * @param withRotation whether the viewing direction is kept
     * @return the location
     */
    public static ServerLocation locationToServerLocation(@Nullable World world, Location location, boolean withRotation) {
        if (withRotation) {
            return new ServerLocation(world == null ? null : world.getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        } else {
            return new ServerLocation(world == null ? null : world.getName(), location.getX(), location.getY(), location.getZ());
        }
    }

    /**
     * @return the Bukkit location in the location's world; without a world name the world is {@code null}
     */
    public static Location serverLocationToLocation(ServerLocation serverLocation) {
        return serverLocationToLocation(null, serverLocation);
    }

    /**
     * @param world used if the location has no world name or its world is not loaded
     * @return the Bukkit location
     */
    public static Location serverLocationToLocation(@Nullable World world, ServerLocation serverLocation) {
        var finalWorld = world;
        if (serverLocation.getWorldName() != null) {
            var world1 = Bukkit.getWorld(serverLocation.getWorldName());
            if (world1 != null) {
                finalWorld = world1;
            }
        }
        var x = serverLocation.getX();
        var y = serverLocation.getY();
        var z = serverLocation.getZ();
        var yaw = serverLocation.getYaw();
        var pitch = serverLocation.getPitch();
        return new Location(finalWorld, x, y, z, yaw, pitch);
    }

}
