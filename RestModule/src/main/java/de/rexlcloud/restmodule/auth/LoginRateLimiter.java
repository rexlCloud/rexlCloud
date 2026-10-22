package de.rexlcloud.restmodule.auth;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks further login attempts for an address and username after too many failures.
 * Keyed by both, so a single client failing logins cannot lock everybody behind the same proxy out.
 */
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String address, String username) {
        var current = attempts.get(key(address, username));
        if (current == null) {
            return false;
        }
        if (current.windowStart + WINDOW.toMillis() < System.currentTimeMillis()) {
            attempts.remove(key(address, username));
            return false;
        }
        return current.failures >= MAX_FAILURES;
    }

    public void recordFailure(String address, String username) {
        var now = System.currentTimeMillis();
        attempts.compute(key(address, username), (key, current) -> {
            if (current == null || current.windowStart + WINDOW.toMillis() < now) {
                return new Attempts(now, 1);
            }
            return new Attempts(current.windowStart, current.failures + 1);
        });
        // Keep the map from growing without bounds
        if (attempts.size() > 10_000) {
            attempts.values().removeIf(entry -> entry.windowStart + WINDOW.toMillis() < now);
        }
    }

    public void reset(String address, String username) {
        attempts.remove(key(address, username));
    }

    public long getWindowSeconds() {
        return WINDOW.toSeconds();
    }

    private static String key(String address, String username) {
        return address + "|" + username.toLowerCase(Locale.ROOT);
    }

    private record Attempts(long windowStart, int failures) {
    }

}
