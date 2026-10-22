package de.rexlcloud.wrapper.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How long to wait before reporting a crashed server as stopped. The master then starts a replacement, so servers
 * that crash right after starting (e.g. a wrong Java version) would otherwise restart in a tight loop.
 */
final class CrashBackoff {

    /**
     * A server that ran this long counts as healthy, so its group starts over with short delays.
     */
    static final long HEALTHY_RUN_MILLIS = 60_000;
    private static final long MIN_DELAY_MILLIS = 5_000;
    private static final long MAX_DELAY_MILLIS = 300_000;

    private static final Map<String, Integer> QUICK_CRASHES = new ConcurrentHashMap<>();

    private CrashBackoff() {
    }

    /**
     * @param group      group of the crashed server
     * @param ranMillis  how long the server ran
     * @return milliseconds to wait before reporting the crash: 0 after a long run, then 5 s, 10 s, 20 s … up to
     * 5 minutes for repeated crashes within the first minute
     */
    static long delayMillis(String group, long ranMillis) {
        if (ranMillis >= HEALTHY_RUN_MILLIS) {
            QUICK_CRASHES.remove(group);
            return 0;
        }
        var crashes = QUICK_CRASHES.merge(group, 1, Integer::sum);
        return Math.min(MAX_DELAY_MILLIS, MIN_DELAY_MILLIS << Math.min(crashes - 1, 10));
    }

}
