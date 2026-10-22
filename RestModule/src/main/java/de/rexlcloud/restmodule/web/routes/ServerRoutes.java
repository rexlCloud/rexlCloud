package de.rexlcloud.restmodule.web.routes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.rexlcloud.restmodule.auth.SessionManager;
import de.rexlcloud.restmodule.user.Permission;
import de.rexlcloud.restmodule.web.Api;
import de.rexlcloud.restmodule.web.ApiException;
import de.rexlcloud.restmodule.web.JsonBody;
import io.javalin.http.Context;
import lombok.AllArgsConstructor;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.ws;

@AllArgsConstructor
public class ServerRoutes {

    private static final int MAX_COMMAND_LENGTH = 1000;

    private final SessionManager sessionManager;
    private final ConsoleSocket consoleSocket;

    public void register() {
        path("servers", () -> {
            get(ctx -> {
                Api.require(ctx, Permission.SEE_ALL_SERVERS);
                ctx.json(Api.moduleApi().getServerInfos(null));
            });
            path("{name}", () -> {
                get(ctx -> {
                    Api.require(ctx, Permission.SEE_ALL_SERVERS);
                    ctx.json(findServer(ctx.pathParam("name")));
                });
                get("console", this::console);
                post("console/ticket", this::consoleTicket);
                ws("console/ws", consoleSocket::configure);
                post("command", this::command);
                post("stop", this::stop);
                post("kill", ctx -> {
                    Api.require(ctx, Permission.STOP_SERVER);
                    var name = findServerName(ctx);
                    Api.respond(ctx, Api.moduleApi().kill(name));
                    Api.audit(ctx, "killed the server %s", name);
                });
                post("save-template", ctx -> {
                    Api.require(ctx, Permission.MANAGE_TEMPLATES);
                    var name = findServerName(ctx);
                    var variant = JsonBody.of(ctx).optionalString("variant");
                    variant = variant == null || variant.isBlank() ? "default" : variant.strip();
                    var result = Api.moduleApi().saveServerToTemplate(name, variant);
                    Api.respond(ctx, result);
                    if (result.isSuccess()) {
                        Api.audit(ctx, "saved the server %s into the template variant %s", name, variant);
                    }
                });
                post("restart", ctx -> {
                    Api.require(ctx, Permission.STOP_SERVER, Permission.START_SERVER);
                    var name = findServerName(ctx);
                    Api.respond(ctx, Api.moduleApi().restart(name));
                    Api.audit(ctx, "restarted the server %s", name);
                });
            });
        });
    }

    private void console(Context ctx) {
        Api.require(ctx, Permission.SEE_SERVER_CONSOLE);
        var name = findServerName(ctx);
        var lines = new JsonArray();
        for (var line : Api.moduleApi().getServerConsole(name)) {
            lines.add(line);
        }
        var json = new JsonObject();
        json.add("lines", lines);
        ctx.json(json);
    }

    private void consoleTicket(Context ctx) {
        var user = Api.require(ctx, Permission.SEE_SERVER_CONSOLE);
        var name = findServerName(ctx);
        var ticket = sessionManager.createConsoleTicket(user, name);
        var json = new JsonObject();
        json.addProperty("ticket", ticket.ticket());
        json.addProperty("expires_in", sessionManager.getTicketTimeoutSeconds());
        json.addProperty("path", "/api/v2/servers/" + name + "/console/ws?ticket=" + ticket.ticket());
        ctx.json(json);
    }

    private void command(Context ctx) {
        Api.require(ctx, Permission.EXECUTE_ON_SERVER);
        var server = findServer(ctx.pathParam("name"));
        var command = JsonBody.of(ctx).requireString("command").strip();
        if (command.length() > MAX_COMMAND_LENGTH) {
            throw ApiException.badRequest("Commands can have at most " + MAX_COMMAND_LENGTH + " characters");
        }
        var name = server.get("server_name").getAsString();
        if (!"online".equals(server.get("online_state").getAsString())) {
            throw ApiException.conflict("The server " + name + " is not online");
        }
        Api.moduleApi().execute(name, command);
        Api.audit(ctx, "executed '%s' on %s", command, name);
        Api.ok(ctx);
    }

    private void stop(Context ctx) {
        Api.require(ctx, Permission.STOP_SERVER);
        var name = findServerName(ctx);
        if (!Api.moduleApi().stop(name)) {
            throw ApiException.notFound("Server " + name + " does not exist");
        }
        Api.audit(ctx, "stopped the server %s", name);
        ctx.status(202).json(Api.status("accepted", "Server " + name + " is stopping"));
    }

    private String findServerName(Context ctx) {
        return findServer(ctx.pathParam("name")).get("server_name").getAsString();
    }

    static JsonObject findServer(String name) {
        var servers = Api.moduleApi().getServerInfos(name);
        if (servers.isEmpty()) {
            throw ApiException.notFound("Server " + name + " does not exist");
        }
        return servers.get(0).getAsJsonObject();
    }

}
