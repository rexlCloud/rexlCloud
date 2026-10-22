package de.rexlcloud.core.network;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Used by the cloud plugins when their connection to the wrapper breaks, e.g. because the wrapper restarts while its
 * servers keep running: they try to connect again every few seconds and only shut the server down if the wrapper
 * stays away.
 */
public final class WrapperReconnect {

    public static final long GIVE_UP_MILLIS = 120_000;
    private static final long RETRY_SECONDS = 5;

    private final Runnable connect;
    private final Runnable giveUp;
    private final Consumer<String> log;
    private final long giveUpMillis;
    private long lostAt;
    private boolean pending;
    private boolean stopped;

    /**
     * @param connect opens a new connection to the wrapper
     * @param giveUp  shuts the server down
     */
    public WrapperReconnect(Runnable connect, Runnable giveUp, Consumer<String> log) {
        this(connect, giveUp, log, GIVE_UP_MILLIS);
    }

    WrapperReconnect(Runnable connect, Runnable giveUp, Consumer<String> log, long giveUpMillis) {
        this.connect = connect;
        this.giveUp = giveUp;
        this.log = log;
        this.giveUpMillis = giveUpMillis;
    }

    /**
     * The connection is up (again).
     */
    public synchronized void connected() {
        if (lostAt != 0) {
            log.accept("Connected to the wrapper again");
        }
        lostAt = 0;
    }

    /**
     * The connection closed, or an attempt to open it failed.
     */
    public synchronized void lost() {
        if (stopped || pending) {
            return;
        }
        var now = System.currentTimeMillis();
        if (lostAt == 0) {
            lostAt = now;
            log.accept("Lost the connection to the wrapper; trying to connect again for " + giveUpMillis / 1000 + " seconds");
        }
        if (now - lostAt >= giveUpMillis) {
            stopped = true;
            log.accept("The wrapper did not come back, stopping the server");
            giveUp.run();
            return;
        }
        pending = true;
        CompletableFuture.delayedExecutor(RETRY_SECONDS, TimeUnit.SECONDS).execute(() -> {
            synchronized (this) {
                pending = false;
                if (stopped) {
                    return;
                }
            }
            connect.run();
        });
    }

    /**
     * The server is shutting down: no more attempts.
     */
    public synchronized void stop() {
        stopped = true;
    }

}
