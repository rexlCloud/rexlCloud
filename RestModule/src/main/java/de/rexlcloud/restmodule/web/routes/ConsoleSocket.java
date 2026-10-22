package de.rexlcloud.restmodule.web.routes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.restmodule.auth.SessionManager;
import de.rexlcloud.restmodule.user.Permission;
import de.rexlcloud.restmodule.user.UserManager;
import de.rexlcloud.restmodule.web.Api;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Streams a server console over a WebSocket.
 * <p>
 * The client connects with a ticket from {@code POST /servers/{name}/console/ticket}. It receives
 * {@code {"type":"history","lines":[...]}} once and then {@code {"type":"line","line":"..."}} for every new line.
 * Users allowed to execute commands can send {@code {"type":"command","command":"..."}}.
 */
@RequiredArgsConstructor
public class ConsoleSocket {

    /** Close code for a missing, expired or foreign ticket (4000-4999 are free for applications). */
    private static final int CLOSE_UNAUTHORIZED = 4401;
    private static final int CLOSE_NOT_FOUND = 4404;
    private static final int MAX_COMMAND_LENGTH = 1000;

    private final SessionManager sessionManager;
    private final UserManager userManager;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> {
            var serverName = ctx.pathParam("name");
            var ticket = sessionManager.redeemConsoleTicket(ctx.queryParam("ticket"))
                    .filter(t -> t.serverName().equals(serverName))
                    .orElse(null);
            var user = ticket == null ? null : userManager.getUser(ticket.username())
                    .filter(u -> u.hasPermission(Permission.SEE_SERVER_CONSOLE))
                    .orElse(null);
            if (user == null) {
                ctx.closeSession(CLOSE_UNAUTHORIZED, "Invalid or expired ticket");
                return;
            }

            var connection = new Connection(ctx, user.getUsername(), serverName,
                    user.hasPermission(Permission.EXECUTE_ON_SERVER));
            // Hold the lock while subscribing and sending the history, so live lines arrive after it
            synchronized (connection) {
                var subscription = Api.moduleApi().subscribeConsole(serverName, connection::sendLine);
                if (subscription.isEmpty()) {
                    ctx.closeSession(CLOSE_NOT_FOUND, "Server not found");
                    return;
                }
                connection.subscription = subscription.get();
                connections.put(ctx.sessionId(), connection);
                var history = new JsonArray();
                for (var line : Api.moduleApi().getServerConsole(serverName)) {
                    history.add(line);
                }
                var json = new JsonObject();
                json.addProperty("type", "history");
                json.addProperty("can_execute", connection.canExecute);
                json.add("lines", history);
                ctx.send(json.toString());
            }
            ctx.enableAutomaticPings(15, TimeUnit.SECONDS);
        });
        ws.onMessage(ctx -> {
            var connection = connections.get(ctx.sessionId());
            if (connection == null) {
                return;
            }
            try {
                var message = JsonParser.parseString(ctx.message()).getAsJsonObject();
                if (!message.has("type") || !"command".equals(message.get("type").getAsString())) {
                    return;
                }
                if (!connection.canExecute) {
                    connection.sendError("Missing permission EXECUTE_ON_SERVER");
                    return;
                }
                var command = message.get("command").getAsString().strip();
                if (command.isEmpty() || command.length() > MAX_COMMAND_LENGTH) {
                    connection.sendError("Commands need between 1 and " + MAX_COMMAND_LENGTH + " characters");
                    return;
                }
                if (!Api.moduleApi().execute(connection.serverName, command)) {
                    connection.sendError("The server is not available");
                    return;
                }
                Console.info("[RestModule] %s executed '%s' on %s", connection.username, command, connection.serverName);
            } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException e) {
                connection.sendError("Invalid message");
            }
        });
        ws.onClose(ctx -> close(ctx.sessionId()));
        ws.onError(ctx -> close(ctx.sessionId()));
    }

    /**
     * Closes all open console connections, e.g. when the module is disabled.
     */
    public void closeAll() {
        connections.keySet().forEach(this::close);
    }

    private void close(String sessionId) {
        var connection = connections.remove(sessionId);
        if (connection != null && connection.subscription != null) {
            try {
                connection.subscription.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class Connection {
        private final WsContext ctx;
        private final String username;
        private final String serverName;
        private final boolean canExecute;
        private AutoCloseable subscription;

        private Connection(WsContext ctx, String username, String serverName, boolean canExecute) {
            this.ctx = ctx;
            this.username = username;
            this.serverName = serverName;
            this.canExecute = canExecute;
        }

        private synchronized void sendLine(String line) {
            var json = new JsonObject();
            json.addProperty("type", "line");
            json.addProperty("line", line);
            send(json);
        }

        private synchronized void sendError(String message) {
            var json = new JsonObject();
            json.addProperty("type", "error");
            json.addProperty("message", message);
            send(json);
        }

        private void send(JsonObject json) {
            if (ctx.session.isOpen()) {
                ctx.send(json.toString());
            }
        }
    }

}
