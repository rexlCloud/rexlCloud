package de.rexlcloud.restmodule.web.routes;

import de.rexlcloud.restmodule.user.Permission;
import de.rexlcloud.restmodule.web.Api;
import de.rexlcloud.restmodule.web.ApiException;
import de.rexlcloud.restmodule.web.JsonBody;

import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

public class WrapperRoutes {

    public void register() {
        path("wrappers", () -> {
            get(ctx -> {
                Api.require(ctx, Permission.SEE_WRAPPERS);
                ctx.json(Api.moduleApi().getWrappers());
            });
            post(ctx -> {
                Api.require(ctx, Permission.MANAGE_WRAPPERS);
                var body = JsonBody.of(ctx);
                var address = body.requireString("address");
                var keyText = body.optionalString("key");
                UUID key = null;
                if (keyText != null && !keyText.isBlank()) {
                    try {
                        key = UUID.fromString(keyText.strip());
                    } catch (IllegalArgumentException e) {
                        throw ApiException.badRequest("The key must be a UUID");
                    }
                }
                var result = Api.moduleApi().addWrapper(address, key);
                if (result.isSuccess()) {
                    ctx.status(201);
                }
                Api.respond(ctx, result);
                Api.audit(ctx, "registered a wrapper for %s", address);
            });
            post("{name}/stop", ctx -> {
                Api.require(ctx, Permission.MANAGE_WRAPPERS);
                var name = ctx.pathParam("name");
                Api.respond(ctx, Api.moduleApi().stopWrapper(name));
                Api.audit(ctx, "stopped the wrapper %s", name);
            });
        });
    }

}
