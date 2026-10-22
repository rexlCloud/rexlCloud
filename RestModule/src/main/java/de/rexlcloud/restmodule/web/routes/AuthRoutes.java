package de.rexlcloud.restmodule.web.routes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.restmodule.auth.LoginRateLimiter;
import de.rexlcloud.restmodule.auth.SessionManager;
import de.rexlcloud.restmodule.user.Encryption;
import de.rexlcloud.restmodule.user.User;
import de.rexlcloud.restmodule.user.UserManager;
import de.rexlcloud.restmodule.web.Api;
import de.rexlcloud.restmodule.web.ApiException;
import de.rexlcloud.restmodule.web.JsonBody;
import io.javalin.http.Context;
import lombok.AllArgsConstructor;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

@AllArgsConstructor
public class AuthRoutes {

    private final UserManager userManager;
    private final SessionManager sessionManager;
    private final LoginRateLimiter rateLimiter;

    public void register() {
        path("auth", () -> {
            post("login", this::login);
            post("logout", this::logout);
            get("me", ctx -> ctx.json(userJson(Api.user(ctx))));
            post("password", this::changePassword);
        });
    }

    private void login(Context ctx) {
        var body = JsonBody.of(ctx);
        var username = body.requireString("username");
        var password = body.requireString("password");
        if (rateLimiter.isBlocked(ctx.ip(), username)) {
            throw ApiException.tooManyRequests("Too many failed logins, try again in "
                    + rateLimiter.getWindowSeconds() / 60 + " minutes");
        }
        var user = userManager.getUser(username).orElse(null);
        if (user == null) {
            // Hash anyway so the response time does not reveal whether the user exists
            Encryption.hash(password, Encryption.generateSalt(), Encryption.HashAlgorithm.CURRENT);
        }
        if (user == null || !user.checkPassword(password)) {
            rateLimiter.recordFailure(ctx.ip(), username);
            throw ApiException.unauthorized("Wrong username or password");
        }
        rateLimiter.reset(ctx.ip(), username);
        var session = sessionManager.createSession(user);
        var json = new JsonObject();
        json.addProperty("token", session.getToken());
        json.addProperty("expires_at", session.getExpiresAt());
        json.add("user", userJson(user));
        ctx.json(json);
    }

    private void logout(Context ctx) {
        Api.user(ctx);
        sessionManager.invalidate(Api.token(ctx));
        Api.ok(ctx);
    }

    private void changePassword(Context ctx) {
        var user = Api.user(ctx);
        var body = JsonBody.of(ctx);
        var currentPassword = body.requireString("current_password");
        var newPassword = body.requireString("new_password");
        if (newPassword.length() < UserManager.MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("The new password needs at least " + UserManager.MIN_PASSWORD_LENGTH + " characters");
        }
        if (!user.changePassword(newPassword, currentPassword)) {
            throw ApiException.badRequest("The current password is wrong");
        }
        // Other devices have to log in again with the new password
        sessionManager.invalidateAll(user.getUsername(), Api.token(ctx));
        userManager.passwordChanged(user);
        Console.info("[RestModule] %s changed their password", user.getUsername());
        Api.ok(ctx);
    }

    static JsonObject userJson(User user) {
        var json = new JsonObject();
        json.addProperty("username", user.getUsername());
        var permissions = new JsonArray();
        user.getPermissionNames().forEach(permissions::add);
        json.add("permissions", permissions);
        return json;
    }

}
