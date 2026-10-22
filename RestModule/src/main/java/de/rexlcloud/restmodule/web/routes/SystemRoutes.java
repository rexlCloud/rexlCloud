package de.rexlcloud.restmodule.web.routes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.rexlcloud.restmodule.RestModule;
import de.rexlcloud.restmodule.user.Permission;
import de.rexlcloud.restmodule.web.Api;
import de.rexlcloud.restmodule.web.ApiException;
import de.rexlcloud.restmodule.web.JsonBody;
import io.javalin.http.Context;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

public class SystemRoutes {

    private static final int MAX_STATS = 1440;

    public void register() {
        get("overview", ctx -> {
            Api.user(ctx);
            ctx.json(Api.moduleApi().getOverview());
        });
        get("stats", ctx -> {
            Api.user(ctx);
            var limit = ctx.queryParam("limit") == null ? 100 : parseLimit(ctx.queryParam("limit"));
            ctx.json(Api.moduleApi().getServerStats(limit));
        });
        get("permissions", ctx -> {
            Api.user(ctx);
            var permissions = new JsonArray();
            for (var permission : Permission.values()) {
                permissions.add(permission.name());
            }
            ctx.json(permissions);
        });
        path("maintenance", () -> {
            get(ctx -> {
                Api.user(ctx);
                ctx.json(maintenanceJson());
            });
            put(ctx -> {
                Api.require(ctx, Permission.MANAGE_MAINTENANCE);
                var enabled = JsonBody.of(ctx).requireBoolean("enabled");
                Api.moduleApi().setMaintenance(enabled);
                Api.audit(ctx, enabled ? "enabled the maintenance mode" : "disabled the maintenance mode");
                ctx.json(maintenanceJson());
            });
        });
        path("motd", () -> {
            get(ctx -> {
                Api.require(ctx, Permission.MANAGE_MAINTENANCE);
                ctx.json(Api.moduleApi().getMotd());
            });
            put(ctx -> {
                Api.require(ctx, Permission.MANAGE_MAINTENANCE);
                var body = JsonBody.of(ctx);
                var motd = body.optionalString("motd");
                var maintenanceMotd = body.optionalString("maintenance_motd");
                if (motd == null && maintenanceMotd == null) {
                    throw ApiException.badRequest("Nothing to change");
                }
                var result = Api.moduleApi().setMotd(motd, maintenanceMotd);
                if (!result.isSuccess()) {
                    Api.respond(ctx, result);
                    return;
                }
                Api.audit(ctx, "changed the MOTD");
                ctx.json(Api.moduleApi().getMotd());
            });
            // Renders a text like the proxies do, for previews while editing
            post("preview", ctx -> {
                Api.require(ctx, Permission.MANAGE_MAINTENANCE);
                var text = JsonBody.of(ctx).requireString("text");
                if (text.length() > 1000) {
                    throw ApiException.badRequest("The text may have at most 1000 characters");
                }
                var json = new JsonObject();
                json.add("component", JsonParser.parseString(Api.moduleApi().renderText(text)));
                ctx.json(json);
            });
        });
        path("modules", () -> {
            get(ctx -> {
                Api.require(ctx, Permission.MANAGE_MODULES);
                var modules = Api.moduleApi().getModules();
                modules.addProperty("self", RestModule.getInstance().getModuleInfo().getName());
                ctx.json(modules);
            });
            post("load", ctx -> {
                Api.require(ctx, Permission.MANAGE_MODULES);
                var file = JsonBody.of(ctx).requireString("file");
                Api.respond(ctx, Api.moduleApi().loadModule(file));
                Api.audit(ctx, "loaded the module file %s", file);
            });
            post("{name}/reload", ctx -> {
                Api.require(ctx, Permission.MANAGE_MODULES);
                var name = ctx.pathParam("name");
                Api.respond(ctx, Api.moduleApi().reloadModule(name));
                Api.audit(ctx, "reloaded the module %s", name);
            });
            post("{name}/disable", this::disableModule);
        });
    }

    private void disableModule(Context ctx) {
        Api.require(ctx, Permission.MANAGE_MODULES);
        var name = ctx.pathParam("name");
        if (name.equals(RestModule.getInstance().getModuleInfo().getName())) {
            // Disabling this module would stop the web server while it answers this request
            throw ApiException.conflict("The RestModule cannot disable itself, use the cloud console instead");
        }
        Api.respond(ctx, Api.moduleApi().disableModule(name));
        Api.audit(ctx, "disabled the module %s", name);
    }

    private static JsonObject maintenanceJson() {
        var json = new JsonObject();
        json.addProperty("enabled", Api.moduleApi().isMaintenance());
        return json;
    }

    private static int parseLimit(String value) {
        try {
            var limit = Integer.parseInt(value);
            if (limit >= 1 && limit <= MAX_STATS) {
                return limit;
            }
        } catch (NumberFormatException ignored) {
        }
        throw ApiException.badRequest("The limit must be between 1 and " + MAX_STATS);
    }

}
