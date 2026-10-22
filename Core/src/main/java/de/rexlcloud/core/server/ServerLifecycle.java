package de.rexlcloud.core.server;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a server is in its life. A server only moves along the transitions below, so it can for example never be
 * started while it is still stopping, or be started again once it is stopped for good.
 *
 * <pre>
 * WAITING ──► STARTING ──► ONLINE
 *    ▲           │           │
 *    │           ▼           ▼
 *    └──────── STOPPING ◄────┘
 *                │
 *                ▼
 *             STOPPED
 * </pre>
 */
public enum ServerLifecycle {

    /**
     * Registered, waits to be started (master only).
     */
    WAITING,
    /**
     * Start sent to the wrapper or process launched; the server has not connected to the cloud yet.
     */
    STARTING,
    /**
     * The server is connected and can take players.
     */
    ONLINE,
    /**
     * Stopping or stopped unexpectedly; files are cleaned up. Afterwards the server waits again (to keep a group's
     * minimum) or is stopped for good.
     */
    STOPPING,
    /**
     * Gone for good, never started again.
     */
    STOPPED;

    private Set<ServerLifecycle> next() {
        return switch (this) {
            case WAITING -> EnumSet.of(STARTING, STOPPED);
            // STARTING -> WAITING: no wrapper could take the server, it is tried again later
            case STARTING -> EnumSet.of(ONLINE, STOPPING, WAITING);
            case ONLINE -> EnumSet.of(STOPPING);
            case STOPPING -> EnumSet.of(WAITING, STOPPED);
            case STOPPED -> EnumSet.noneOf(ServerLifecycle.class);
        };
    }

    public boolean canBecome(ServerLifecycle next) {
        return next().contains(next);
    }

    /**
     * Running or about to: STARTING or ONLINE.
     */
    public boolean isActive() {
        return this == STARTING || this == ONLINE;
    }

}
