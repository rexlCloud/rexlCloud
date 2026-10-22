package de.rexlcloud.restmodule.web;

import com.google.gson.JsonObject;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.restmodule.auth.LoginRateLimiter;
import de.rexlcloud.restmodule.auth.SessionManager;
import de.rexlcloud.restmodule.config.RestConfig;
import de.rexlcloud.restmodule.user.UserManager;
import de.rexlcloud.restmodule.web.routes.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import static io.javalin.apibuilder.ApiBuilder.before;
import static io.javalin.apibuilder.ApiBuilder.path;

public class WebServer {

    private static final String API_PREFIX = "/api/v2";
    private static final String BEARER = "Bearer ";

    private final RestConfig config;
    private final Path dataFolder;
    private final UserManager userManager;
    private final SessionManager sessionManager;
    private final ConsoleSocket consoleSocket;
    private Javalin app;
    private Https https;

    public WebServer(RestConfig config, Path dataFolder, UserManager userManager, SessionManager sessionManager) {
        this.config = config;
        this.dataFolder = dataFolder;
        this.userManager = userManager;
        this.sessionManager = sessionManager;
        this.consoleSocket = new ConsoleSocket(sessionManager, userManager);
    }

    public void start() {
        var tls = config.getTls();
        if (tls != null && tls.enabled()) {
            try {
                https = new Https(resolve(tls.certificate()), resolve(tls.privateKey()));
            } catch (IOException | GeneralSecurityException e) {
                // Never fall back to plain HTTP: logins and tokens would go over the network unencrypted
                throw new IllegalStateException("HTTPS is enabled, but the certificate could not be loaded: "
                        + e.getMessage(), e);
            }
        }
        var authRoutes = new AuthRoutes(userManager, sessionManager, new LoginRateLimiter());
        var serverRoutes = new ServerRoutes(sessionManager, consoleSocket);
        var groupRoutes = new GroupRoutes();
        var playerRoutes = new PlayerRoutes();
        var wrapperRoutes = new WrapperRoutes();
        var systemRoutes = new SystemRoutes();
        var userRoutes = new UserRoutes(userManager, sessionManager);
        var metricsRoutes = new MetricsRoutes(config);
        var templateRoutes = new TemplateRoutes();

        // Jetty and Javalin look up services through the context class loader, which has to see the module jar
        var thread = Thread.currentThread();
        var previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(getClass().getClassLoader());
        try {
            app = Javalin.create(javalin -> {
                javalin.startup.showJavalinBanner = false;
                javalin.startup.showOldJavalinVersionWarning = false;
                javalin.jetty.host = config.getHost();
                javalin.jetty.port = config.getPort();
                if (https != null) {
                    javalin.jetty.addConnector((server, httpConfiguration) ->
                            https.connector(server, httpConfiguration, config.getHost(), config.getPort()));
                }
                javalin.jsonMapper(new GsonJsonMapper());

                javalin.routes.exception(ApiException.class, (e, ctx) -> error(ctx, e.getStatus(), e.getError(), e.getMessage()));
                javalin.routes.exception(HttpResponseException.class, (e, ctx) ->
                        error(ctx, e.getStatus(), "http_" + e.getStatus(), e.getMessage()));
                javalin.routes.exception(Exception.class, (e, ctx) -> {
                    Console.error("[RestModule] %s %s failed: %s", ctx.method(), ctx.path(), e);
                    error(ctx, 500, "internal_error", "An internal error occurred");
                });

                javalin.routes.apiBuilder(() -> path(API_PREFIX, () -> {
                    before(this::authenticate);
                    authRoutes.register();
                    systemRoutes.register();
                    serverRoutes.register();
                    groupRoutes.register();
                    playerRoutes.register();
                    wrapperRoutes.register();
                    userRoutes.register();
                    metricsRoutes.register();
                    templateRoutes.register();
                }));
            }).start();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
        if (https != null) {
            https.watch();
        }
        Console.info("[RestModule] REST API listening on %s://%s:%s%s", https != null ? "https" : "http", config.getHost(),
                config.getPort(), API_PREFIX);
    }

    /**
     * Resolves the bearer token of every request except the login and the ticket-secured console socket.
     * Routes decide themselves whether a login is required.
     */
    private void authenticate(Context ctx) {
        var path = ctx.path();
        // The metrics check their own token
        if (path.equals(API_PREFIX + "/auth/login") || path.endsWith("/console/ws") || path.equals(API_PREFIX + "/metrics")) {
            return;
        }
        var header = ctx.header("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw ApiException.unauthorized("Missing login token");
        }
        var token = header.substring(BEARER.length()).strip();
        var user = sessionManager.authenticate(token)
                .orElseThrow(() -> ApiException.unauthorized("The login token is invalid or expired"));
        ctx.attribute(Api.USER_ATTRIBUTE, user);
        ctx.attribute(Api.TOKEN_ATTRIBUTE, token);
    }

    /**
     * Loads the certificate files again if they changed; otherwise done every minute.
     */
    void reloadCertificate() {
        if (https != null) {
            https.reloadIfChanged();
        }
    }

    private Path resolve(String file) {
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("HTTPS is enabled, but tls.certificate or tls.privateKey is not set");
        }
        return dataFolder.resolve(file);
    }

    private static void error(Context ctx, int status, String error, String message) {
        var json = new JsonObject();
        json.addProperty("error", error);
        json.addProperty("message", message);
        ctx.status(status).json(json);
    }

    public void stop() {
        consoleSocket.closeAll();
        if (https != null) {
            https.stop();
        }
        if (app != null) {
            app.stop();
        }
    }

}
