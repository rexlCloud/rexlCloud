package de.rexlcloud.restmodule.web.routes;

import de.rexlcloud.restmodule.user.Permission;
import de.rexlcloud.restmodule.web.Api;
import de.rexlcloud.restmodule.web.ApiException;
import io.javalin.http.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

/**
 * The template files on the master: {@code /templates/{group}/{variant}/...} with the path inside the template as
 * the query parameter {@code path}. The global templates are {@code global/server} and {@code global/proxy}.
 */
public class TemplateRoutes {

    public void register() {
        path("templates", () -> {
            get(ctx -> {
                Api.require(ctx, Permission.MANAGE_TEMPLATES);
                ctx.json(Api.moduleApi().getTemplates());
            });
            path("{group}/{variant}", () -> {
                get("files", ctx -> {
                    Api.require(ctx, Permission.MANAGE_TEMPLATES);
                    var result = Api.moduleApi().listTemplateFiles(group(ctx), variant(ctx), ctx.queryParam("path"));
                    if (result.isSuccess()) {
                        ctx.json(result.data());
                    } else {
                        Api.respond(ctx, result);
                    }
                });
                get("file", this::download);
                put("file", ctx -> {
                    Api.require(ctx, Permission.MANAGE_TEMPLATES);
                    var path = requirePath(ctx);
                    // Streamed, so a server.jar of a few hundred MB does not have to fit into memory
                    var result = Api.moduleApi().writeTemplateFile(group(ctx), variant(ctx), path, ctx.bodyInputStream());
                    Api.respond(ctx, result);
                    if (result.isSuccess()) {
                        Api.audit(ctx, "saved the template file %s/%s/%s", group(ctx), variant(ctx), path);
                    }
                });
                post("folder", ctx -> {
                    Api.require(ctx, Permission.MANAGE_TEMPLATES);
                    Api.respond(ctx, Api.moduleApi().createTemplateFolder(group(ctx), variant(ctx), requirePath(ctx)));
                });
                delete("file", ctx -> {
                    Api.require(ctx, Permission.MANAGE_TEMPLATES);
                    var path = ctx.queryParam("path");
                    var result = Api.moduleApi().deleteTemplateFile(group(ctx), variant(ctx), path);
                    Api.respond(ctx, result);
                    if (result.isSuccess()) {
                        Api.audit(ctx, "deleted %s from the template %s/%s", path == null || path.isBlank() ? "everything" : path,
                                group(ctx), variant(ctx));
                    }
                });
            });
        });
    }

    private void download(Context ctx) throws IOException {
        Api.require(ctx, Permission.MANAGE_TEMPLATES);
        var result = Api.moduleApi().templateFile(group(ctx), variant(ctx), requirePath(ctx));
        if (!result.isSuccess() || result.data() == null) {
            Api.respond(ctx, result);
            return;
        }
        var file = Path.of(result.data().getAsString());
        ctx.header("Content-Disposition", "attachment; filename=\"" + file.getFileName().toString().replace("\"", "") + "\"");
        ctx.header("Content-Length", String.valueOf(Files.size(file)));
        ctx.contentType("application/octet-stream").result(Files.newInputStream(file));
    }

    private static String group(Context ctx) {
        return ctx.pathParam("group");
    }

    private static String variant(Context ctx) {
        return ctx.pathParam("variant");
    }

    private static String requirePath(Context ctx) {
        var path = ctx.queryParam("path");
        if (path == null || path.isBlank()) {
            throw ApiException.badRequest("The query parameter path is missing");
        }
        return path;
    }

}
