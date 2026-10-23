package de.rexlcloud.restmodule.web.routes;

import de.rexlcloud.restmodule.user.Permission;
import de.rexlcloud.restmodule.web.Api;
import de.rexlcloud.restmodule.web.ApiException;
import de.rexlcloud.restmodule.web.JsonBody;
import io.javalin.http.Context;

import java.util.UUID;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

public class PlayerRoutes {

    private static final int MAX_MESSAGE_LENGTH = 256;

    public void register() {
        path("players", () -> {
            get(ctx -> {
                Api.require(ctx, Permission.SEE_PLAYERS);
                ctx.json(Api.moduleApi().getPlayers());
            });
            // {player}: UUID or name; the player only has to have joined once
            get("{player}", ctx -> {
                Api.require(ctx, Permission.SEE_PLAYERS);
                Api.respond(ctx, Api.moduleApi().getPlayer(ctx.pathParam("player")));
            });
            path("{player}/data/{key}", () -> {
                put(ctx -> {
                    Api.require(ctx, Permission.MANAGE_PLAYERS);
                    var player = ctx.pathParam("player");
                    var key = ctx.pathParam("key");
                    var body = JsonBody.of(ctx);
                    Api.respond(ctx, Api.moduleApi().setPlayerData(player, key, body.optionalString("type"),
                            body.requireValue("value")));
                    Api.audit(ctx, "set the player data %s of %s", key, player);
                });
                delete(ctx -> {
                    Api.require(ctx, Permission.MANAGE_PLAYERS);
                    var player = ctx.pathParam("player");
                    var key = ctx.pathParam("key");
                    Api.respond(ctx, Api.moduleApi().removePlayerData(player, key));
                    Api.audit(ctx, "removed the player data %s of %s", key, player);
                });
            });
            path("{uuid}", () -> {
                post("kick", ctx -> {
                    Api.require(ctx, Permission.MANAGE_PLAYERS);
                    var uuid = uuid(ctx);
                    var message = checkLength(JsonBody.of(ctx).optionalString("message"));
                    Api.respond(ctx, Api.moduleApi().kickPlayer(uuid, message));
                    Api.audit(ctx, "kicked the player %s", uuid);
                });
                post("message", ctx -> {
                    Api.require(ctx, Permission.MANAGE_PLAYERS);
                    var uuid = uuid(ctx);
                    var message = checkLength(JsonBody.of(ctx).requireString("message"));
                    Api.respond(ctx, Api.moduleApi().messagePlayer(uuid, message));
                    Api.audit(ctx, "sent a message to the player %s", uuid);
                });
            });
        });
    }

    private static UUID uuid(Context ctx) {
        try {
            return UUID.fromString(ctx.pathParam("uuid"));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid player UUID");
        }
    }

    private static String checkLength(String message) {
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            throw ApiException.badRequest("Messages can have at most " + MAX_MESSAGE_LENGTH + " characters");
        }
        return message;
    }

}
