package de.rexlcloud.restmodule.web.routes;

import com.google.gson.JsonArray;
import de.rexlcloud.restmodule.auth.SessionManager;
import de.rexlcloud.restmodule.user.Permission;
import de.rexlcloud.restmodule.user.User;
import de.rexlcloud.restmodule.user.UserManager;
import de.rexlcloud.restmodule.web.Api;
import de.rexlcloud.restmodule.web.ApiException;
import de.rexlcloud.restmodule.web.JsonBody;
import io.javalin.http.Context;
import lombok.AllArgsConstructor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * Panel user management. Users can only grant or revoke permissions they have themselves.
 */
@AllArgsConstructor
public class UserRoutes {

    private final UserManager userManager;
    private final SessionManager sessionManager;

    public void register() {
        path("users", () -> {
            get(ctx -> {
                Api.require(ctx, Permission.SEE_ALL_USERS);
                var users = new JsonArray();
                userManager.getUsers().forEach(user -> users.add(AuthRoutes.userJson(user)));
                ctx.json(users);
            });
            post(this::create);
            path("{username}", () -> {
                delete(this::deleteUser);
                put("permissions", this::setPermissions);
            });
        });
    }

    private void create(Context ctx) {
        var actor = Api.require(ctx, Permission.CREATE_USER);
        var body = JsonBody.of(ctx);
        var username = body.requireString("username");
        var password = body.requireString("password");
        var permissionNames = body.optionalStringList("permissions");
        if (!UserManager.USERNAME.matcher(username).matches()) {
            throw ApiException.badRequest("Usernames need 3 to 32 letters, numbers, '-' or '_'");
        }
        if (password.length() < UserManager.MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("Passwords need at least " + UserManager.MIN_PASSWORD_LENGTH + " characters");
        }
        var permissions = parsePermissions(permissionNames == null ? List.of() : permissionNames);
        if (!permissions.isEmpty()) {
            Api.require(ctx, Permission.CHANGE_USER_PERMISSIONS);
            checkCanGrant(actor, permissions);
        }
        var user = userManager.createUser(username, password)
                .orElseThrow(() -> ApiException.conflict("A user named " + username + " already exists"));
        if (!permissions.isEmpty()) {
            user.setPermissions(permissions);
        }
        Api.audit(ctx, "created the panel user %s", username);
        ctx.status(201).json(AuthRoutes.userJson(user));
    }

    private void deleteUser(Context ctx) {
        var actor = Api.require(ctx, Permission.DELETE_USER);
        var user = findUser(ctx);
        if (user.getUsername().equals(actor.getUsername())) {
            throw ApiException.conflict("You cannot delete your own account");
        }
        userManager.deleteUser(user);
        sessionManager.invalidateAll(user.getUsername(), null);
        Api.audit(ctx, "deleted the panel user %s", user.getUsername());
        Api.ok(ctx);
    }

    private void setPermissions(Context ctx) {
        var actor = Api.require(ctx, Permission.CHANGE_USER_PERMISSIONS);
        var user = findUser(ctx);
        var names = JsonBody.of(ctx).optionalStringList("permissions");
        if (names == null) {
            throw ApiException.badRequest("The field 'permissions' is required");
        }
        var wanted = parsePermissions(names);
        var changed = EnumSet.noneOf(Permission.class);
        var current = user.getPermissions();
        for (var permission : Permission.values()) {
            if (wanted.contains(permission) != current.contains(permission)) {
                changed.add(permission);
            }
        }
        checkCanGrant(actor, changed);
        if (user.getUsername().equals(actor.getUsername()) && !wanted.contains(Permission.CHANGE_USER_PERMISSIONS)) {
            throw ApiException.conflict("You cannot remove your own permission to change permissions");
        }
        user.setPermissions(wanted);
        Api.audit(ctx, "set the permissions of %s to %s", user.getUsername(), wanted);
        ctx.json(AuthRoutes.userJson(user));
    }

    private User findUser(Context ctx) {
        var username = ctx.pathParam("username");
        return userManager.getUser(username)
                .orElseThrow(() -> ApiException.notFound("User " + username + " does not exist"));
    }

    private static void checkCanGrant(User actor, Set<Permission> permissions) {
        var notOwned = permissions.stream().filter(permission -> !actor.hasPermission(permission)).toList();
        if (!notOwned.isEmpty()) {
            throw ApiException.forbidden("You can only grant or revoke permissions you have yourself: "
                    + notOwned.stream().map(Enum::name).toList());
        }
    }

    private static Set<Permission> parsePermissions(List<String> names) {
        var permissions = EnumSet.noneOf(Permission.class);
        for (var name : names) {
            permissions.add(Permission.fromName(name)
                    .orElseThrow(() -> ApiException.badRequest("Unknown permission " + name)));
        }
        return permissions;
    }

}
