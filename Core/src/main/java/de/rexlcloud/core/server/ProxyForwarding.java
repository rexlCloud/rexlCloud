package de.rexlcloud.core.server;

import lombok.NonNull;

import java.io.Serial;
import java.io.Serializable;

/**
 * How proxies pass the real address and UUID of players on to the servers behind them.
 *
 * @param mode   forwarding used by the whole network
 * @param secret shared secret of {@link Mode#MODERN} forwarding
 */
public record ProxyForwarding(@NonNull Mode mode, @NonNull String secret) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Mode {
        /**
         * Velocity modern forwarding, protected by the secret. Needs Velocity and Paper 1.13 or newer.
         */
        MODERN,
        /**
         * BungeeCord forwarding ({@code bungeecord: true} on the servers). Works with BungeeCord, Velocity and
         * every server version, but anyone who can reach a server directly can join as any player.
         */
        LEGACY
    }

    @Override
    public String toString() {
        // Keeps the secret out of logs
        return "ProxyForwarding[mode=" + mode + "]";
    }

}
