package de.rexlcloud.core.server;

import java.io.Serializable;

/**
 * Whether a group runs servers or proxies.
 */
public enum ServerType implements Serializable {

    /**
     * Velocity or BungeeCord proxies.
     */
    PROXY,
    /**
     * Minecraft servers (Paper, Spigot or a custom jar).
     */
    SERVER

}
