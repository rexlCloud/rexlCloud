package de.rexlcloud.core.api;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * A position in a world of a server, independent of the server software (Bukkit {@code Location}, Minestom
 * {@code Pos}); the Bridge and MinestomBridge convert it. The arithmetic methods change this object and return it.
 */
@Getter
public class ServerLocation implements Serializable {

    /**
     * Name of the world (Minestom: the UUID of the instance), {@code null} for the player's current one.
     */
    @Nullable
    private String worldName = null;
    /**
     * X coordinate.
     */
    private double x;
    /**
     * Y coordinate.
     */
    private double y;
    /**
     * Z coordinate.
     */
    private double z;
    /**
     * Viewing direction in degrees.
     */
    private float yaw = 0, pitch = 0;

    /**
     * A position in the player's current world, looking straight ahead.
     */
    public ServerLocation(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * A position and viewing direction in the player's current world.
     */
    public ServerLocation(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * A position and viewing direction in a world.
     */
    public ServerLocation(@Nullable String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * A position in a world, looking straight ahead.
     */
    public ServerLocation(@Nullable String worldName, double x, double y, double z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Moves this location.
     *
     * @return this location
     */
    public ServerLocation add(double x, double y, double z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    /**
     * Moves this location by the coordinates of another one.
     *
     * @return this location
     */
    public ServerLocation add(ServerLocation serverLocation) {
        this.x += serverLocation.getX();
        this.y += serverLocation.getY();
        this.z += serverLocation.getZ();
        return this;
    }

    /**
     * Moves this location back.
     *
     * @return this location
     */
    public ServerLocation subtract(double x, double y, double z) {
        this.x -= x;
        this.y -= y;
        this.z -= z;
        return this;
    }

    /**
     * Moves this location back by the coordinates of another one.
     *
     * @return this location
     */
    public ServerLocation subtract(ServerLocation serverLocation) {
        this.x -= serverLocation.getX();
        this.y -= serverLocation.getY();
        this.z -= serverLocation.getZ();
        return this;
    }

    /**
     * Multiplies all coordinates.
     *
     * @return this location
     */
    public ServerLocation multiply(double multi) {
        return multiply(multi, multi, multi);
    }

    /**
     * Multiplies each coordinate.
     *
     * @return this location
     */
    public ServerLocation multiply(double multiX, double multiY, double multiZ) {
        this.x *= multiX;
        this.y *= multiY;
        this.z *= multiZ;
        return this;
    }

    @Override
    public String toString() {
        return "ServerLocation{" +
                "worldName='" + worldName + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", yaw=" + yaw +
                ", pitch=" + pitch +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        var that = (ServerLocation) o;

        if (Double.compare(that.x, x) != 0) return false;
        if (Double.compare(that.y, y) != 0) return false;
        if (Double.compare(that.z, z) != 0) return false;
        if (Float.compare(that.yaw, yaw) != 0) return false;
        if (Float.compare(that.pitch, pitch) != 0) return false;
        return Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = worldName != null ? worldName.hashCode() : 0;
        temp = Double.doubleToLongBits(x);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(z);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (yaw != +0.0f ? Float.floatToIntBits(yaw) : 0);
        result = 31 * result + (pitch != +0.0f ? Float.floatToIntBits(pitch) : 0);
        return result;
    }

    /**
     * @return the X coordinate of the block
     */
    public int getBlockX() {
        return (int) x;
    }

    /**
     * @return the Y coordinate of the block
     */
    public int getBlockY() {
        return (int) y;
    }

    /**
     * @return the Z coordinate of the block
     */
    public int getBlockZ() {
        return (int) z;
    }
}