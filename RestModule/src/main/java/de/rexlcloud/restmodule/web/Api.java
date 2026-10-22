package de.rexlcloud.restmodule.web;

import com.google.gson.JsonObject;
import de.rexlcloud.core.api.module.ModuleApi;
import de.rexlcloud.core.api.module.ModuleApiResult;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.restmodule.RestModule;
import de.rexlcloud.restmodule.user.Permission;
import de.rexlcloud.restmodule.user.User;
import io.javalin.http.Context;

import java.util.Arrays;

/**
 * Helpers shared by all route handlers.
 */
public final class Api {

    static final String USER_ATTRIBUTE = "rexlcloud.user";
    static final String TOKEN_ATTRIBUTE = "rexlcloud.token";

    private Api() {
    }

    public static ModuleApi moduleApi() {
        return RestModule.getInstance().getModuleApi();
    }

    public static User user(Context ctx) {
        User user = ctx.attribute(USER_ATTRIBUTE);
        if (user == null) {
            throw ApiException.unauthorized("Missing or invalid login token");
        }
        return user;
    }

    public static String token(Context ctx) {
        return ctx.attribute(TOKEN_ATTRIBUTE);
    }

    /**
     * Returns the logged-in user if they have all the given permissions, otherwise ends the request with 403.
     */
    public static User require(Context ctx, Permission... permissions) {
        var user = user(ctx);
        var missing = Arrays.stream(permissions).filter(permission -> !user.hasPermission(permission)).toList();
        if (!missing.isEmpty()) {
            throw ApiException.forbidden("Missing permission " + missing.stream().map(Enum::name).toList());
        }
        return user;
    }

    /**
     * Writes a {@link ModuleApiResult} as response: 200/202 on success, otherwise the matching error.
     */
    public static void respond(Context ctx, ModuleApiResult result) {
        var message = result.message() == null ? "" : result.message();
        switch (result.status()) {
            case OK -> {
                var json = status("ok", result.message());
                if (result.data() != null) {
                    json.add("data", result.data());
                }
                ctx.json(json);
            }
            case ACCEPTED -> ctx.status(202).json(status("accepted", result.message()));
            case NOT_FOUND -> throw ApiException.notFound(message);
            case CONFLICT -> throw ApiException.conflict(message);
            case INVALID -> throw ApiException.badRequest(message);
        }
    }

    public static void ok(Context ctx) {
        ctx.json(status("ok", null));
    }

    public static JsonObject status(String status, String message) {
        var json = new JsonObject();
        json.addProperty("status", status);
        if (message != null) {
            json.addProperty("message", message);
        }
        return json;
    }

    /**
     * Logs a state-changing action to the cloud console.
     */
    public static void audit(Context ctx, String format, Object... args) {
        Console.info("[RestModule] %s %s", user(ctx).getUsername(), String.format(format, args));
    }

}
