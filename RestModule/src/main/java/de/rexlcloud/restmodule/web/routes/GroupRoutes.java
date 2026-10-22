package de.rexlcloud.restmodule.web.routes;

import com.google.gson.JsonObject;
import de.rexlcloud.restmodule.user.Permission;
import de.rexlcloud.restmodule.web.Api;
import de.rexlcloud.restmodule.web.ApiException;
import de.rexlcloud.restmodule.web.JsonBody;
import io.javalin.http.Context;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.patch;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

public class GroupRoutes {

    private static final int MAX_START_AMOUNT = 50;

    public void register() {
        get("server-jars", ctx -> {
            Api.require(ctx, Permission.MANAGE_GROUPS);
            ctx.json(Api.moduleApi().getServerJars());
        });
        get("java-runtimes", ctx -> {
            Api.require(ctx, Permission.MANAGE_GROUPS);
            ctx.json(Api.moduleApi().getJavaRuntimes());
        });
        path("groups", () -> {
            get(ctx -> {
                Api.require(ctx, Permission.SEE_ALL_GROUPS);
                ctx.json(Api.moduleApi().getServerGroupsInfos(null));
            });
            post(this::create);
            path("{name}", () -> {
                get(ctx -> {
                    Api.require(ctx, Permission.SEE_ALL_GROUPS);
                    ctx.json(findGroup(ctx.pathParam("name")));
                });
                patch(this::edit);
                delete(ctx -> {
                    Api.require(ctx, Permission.MANAGE_GROUPS);
                    var name = ctx.pathParam("name");
                    Api.respond(ctx, Api.moduleApi().deleteServerGroup(name));
                    Api.audit(ctx, "deleted the group %s", name);
                });
                post("start", this::start);
                post("sync-template", ctx -> {
                    Api.require(ctx, Permission.MANAGE_GROUPS);
                    var name = ctx.pathParam("name");
                    Api.respond(ctx, Api.moduleApi().syncServerGroupTemplate(name));
                    Api.audit(ctx, "synced the template of %s", name);
                });
            });
        });
    }

    private void create(Context ctx) {
        Api.require(ctx, Permission.MANAGE_GROUPS);
        var body = JsonBody.of(ctx);
        var name = body.requireString("name");
        var staticServer = Boolean.TRUE.equals(body.optionalBoolean("static"));
        var minServers = body.optionalInt("min_servers");
        var result = Api.moduleApi().createServerGroup(
                name,
                body.requireString("type"),
                body.requireInt("memory"),
                staticServer,
                body.optionalString("wrapper"),
                minServers == null ? 1 : minServers,
                Boolean.TRUE.equals(body.optionalBoolean("fallback")),
                body.requireString("server_jar")
        );
        Api.respond(ctx, result);
        if (result.isSuccess()) {
            Api.audit(ctx, "created the group %s", name);
        }
    }

    private void edit(Context ctx) {
        Api.require(ctx, Permission.MANAGE_GROUPS);
        var name = ctx.pathParam("name");
        var body = JsonBody.of(ctx);
        var memory = body.optionalInt("memory");
        var maxPlayers = body.optionalInt("max_players");
        var minServers = body.optionalInt("min_servers");
        var fallback = body.optionalBoolean("fallback");
        var removeOldLogs = body.optionalBoolean("remove_old_logs");
        var javaCommand = body.optionalString("java_command");
        var otherChanges = memory != null || maxPlayers != null || minServers != null || fallback != null
                || removeOldLogs != null;
        if (!otherChanges && javaCommand == null) {
            throw ApiException.badRequest("Nothing to change");
        }
        if (javaCommand != null) {
            // First, so an unknown runtime rejects the whole request
            var result = Api.moduleApi().setServerGroupJavaRuntime(name, javaCommand);
            if (!result.isSuccess() || !otherChanges) {
                Api.respond(ctx, result);
                if (result.isSuccess()) {
                    Api.audit(ctx, "set the Java runtime of %s to %s", name, javaCommand);
                }
                return;
            }
            Api.audit(ctx, "set the Java runtime of %s to %s", name, javaCommand);
        }
        var result = Api.moduleApi().editServerGroup(name, memory, maxPlayers, minServers, fallback, removeOldLogs);
        Api.respond(ctx, result);
        Api.audit(ctx, "edited the group %s", name);
    }

    private void start(Context ctx) {
        Api.require(ctx, Permission.START_SERVER);
        var group = findGroup(ctx.pathParam("name"));
        var name = group.get("group_name").getAsString();
        var amount = JsonBody.of(ctx).optionalInt("amount");
        amount = amount == null ? 1 : amount;
        if (amount < 1 || amount > MAX_START_AMOUNT) {
            throw ApiException.badRequest("The amount must be between 1 and " + MAX_START_AMOUNT);
        }
        if (group.get("group_static").getAsBoolean() && !group.getAsJsonArray("servers").isEmpty()) {
            throw ApiException.conflict("The static group " + name + " already has a running server");
        }
        if (!Api.moduleApi().start(name, amount)) {
            throw ApiException.conflict("Could not start servers of " + name);
        }
        Api.audit(ctx, "started %d server(s) of %s", amount, name);
        ctx.status(202).json(Api.status("accepted", "Starting " + amount + " server(s) of " + name));
    }

    static JsonObject findGroup(String name) {
        var groups = Api.moduleApi().getServerGroupsInfos(name);
        if (groups.isEmpty()) {
            throw ApiException.notFound("Group " + name + " does not exist");
        }
        return groups.get(0).getAsJsonObject();
    }

}
