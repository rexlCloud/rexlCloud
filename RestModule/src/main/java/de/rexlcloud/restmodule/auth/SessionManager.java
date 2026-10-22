package de.rexlcloud.restmodule.auth;

import de.rexlcloud.restmodule.user.Encryption;
import de.rexlcloud.restmodule.user.User;
import de.rexlcloud.restmodule.user.UserManager;
import lombok.Getter;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps login tokens in memory. Tokens expire after a period of inactivity; every authenticated request extends
 * them. Restarting the module logs everybody out.
 */
public class SessionManager {

    private final UserManager userManager;
    private final Duration sessionTimeout;
    private final Duration ticketTimeout;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, ConsoleTicket> consoleTickets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "rest-session-cleaner");
        thread.setDaemon(true);
        return thread;
    });

    public SessionManager(UserManager userManager, Duration sessionTimeout, Duration ticketTimeout) {
        this.userManager = userManager;
        this.sessionTimeout = sessionTimeout;
        this.ticketTimeout = ticketTimeout;
        cleaner.scheduleAtFixedRate(this::removeExpired, 1, 1, TimeUnit.MINUTES);
    }

    public Session createSession(User user) {
        var session = new Session(Encryption.generateToken(), user.getUsername(), now() + sessionTimeout.toMillis());
        sessions.put(session.getToken(), session);
        return session;
    }

    /**
     * Resolves the user of a token and extends the session.
     */
    public Optional<User> authenticate(String token) {
        if (token == null) {
            return Optional.empty();
        }
        var session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.getExpiresAt() < now()) {
            sessions.remove(token);
            return Optional.empty();
        }
        var user = userManager.getUser(session.getUsername());
        if (user.isEmpty()) {
            sessions.remove(token);
            return Optional.empty();
        }
        session.expiresAt = now() + sessionTimeout.toMillis();
        return user;
    }

    public Optional<Session> getSession(String token) {
        return Optional.ofNullable(token).map(sessions::get);
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /**
     * Logs the user out everywhere except for the given token.
     */
    public void invalidateAll(String username, String exceptToken) {
        sessions.values().removeIf(session -> session.getUsername().equals(username)
                && !session.getToken().equals(exceptToken));
        consoleTickets.values().removeIf(ticket -> ticket.username().equals(username));
    }

    /**
     * Creates a short-lived, single-use ticket for opening the console WebSocket of a server, so the login token
     * never has to be handed to the browser.
     */
    public ConsoleTicket createConsoleTicket(User user, String serverName) {
        var ticket = new ConsoleTicket(Encryption.generateToken(), user.getUsername(), serverName,
                now() + ticketTimeout.toMillis());
        consoleTickets.put(ticket.ticket(), ticket);
        return ticket;
    }

    public Optional<ConsoleTicket> redeemConsoleTicket(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(consoleTickets.remove(ticket)).filter(t -> t.expiresAt() >= now());
    }

    public long getTicketTimeoutSeconds() {
        return ticketTimeout.toSeconds();
    }

    public void shutdown() {
        cleaner.shutdownNow();
        sessions.clear();
        consoleTickets.clear();
    }

    private void removeExpired() {
        var now = now();
        sessions.values().removeIf(session -> session.getExpiresAt() < now);
        consoleTickets.values().removeIf(ticket -> ticket.expiresAt() < now);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    @Getter
    public static class Session {
        private final String token;
        private final String username;
        private volatile long expiresAt;

        private Session(String token, String username, long expiresAt) {
            this.token = token;
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }

    public record ConsoleTicket(String ticket, String username, String serverName, long expiresAt) {
    }

}
