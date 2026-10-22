package de.rexlcloud.restmodule.web.routes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.rexlcloud.restmodule.config.RestConfig;
import de.rexlcloud.restmodule.web.Api;
import de.rexlcloud.restmodule.web.ApiException;
import io.javalin.http.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

import static io.javalin.apibuilder.ApiBuilder.get;

/**
 * {@code GET /metrics} in the Prometheus text format, for monitoring. Scrapers authenticate with
 * {@code metricsToken} from the module's config.json instead of a login, because login tokens expire; without a
 * token the endpoint does not exist.
 */
public class MetricsRoutes {

    private static final String BEARER = "Bearer ";

    private final RestConfig config;

    public MetricsRoutes(RestConfig config) {
        this.config = config;
    }

    public void register() {
        get("metrics", this::metrics);
    }

    private void metrics(Context ctx) {
        var token = config.getMetricsToken();
        if (token == null || token.isBlank()) {
            throw ApiException.notFound("Metrics are off; set metricsToken in the RestModule's config.json");
        }
        var header = ctx.header("Authorization");
        var sent = header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()).strip() : "";
        if (!MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.unauthorized("Wrong or missing metrics token");
        }
        ctx.contentType("text/plain; version=0.0.4; charset=utf-8").result(render());
    }

    static String render() {
        var api = Api.moduleApi();
        var out = new StringBuilder();
        var overview = api.getOverview();

        gauge(out, "rexlcloud_players_online", "Players on the network");
        sample(out, "rexlcloud_players_online", Map.of(), number(overview, "players_online"));
        gauge(out, "rexlcloud_maintenance", "1 while maintenance mode is on");
        sample(out, "rexlcloud_maintenance", Map.of(), api.isMaintenance() ? 1 : 0);

        var servers = api.getServerInfos(null);
        var perGroupAndState = new TreeMap<String, Integer>();
        gauge(out, "rexlcloud_server_players", "Players on a server");
        for (var element : servers) {
            var server = element.getAsJsonObject();
            var group = text(server, "server_group");
            var state = text(server, "online_state");
            perGroupAndState.merge(group + "\n" + state, 1, Integer::sum);
            sample(out, "rexlcloud_server_players",
                    Map.of("server", text(server, "server_name"), "group", group, "state", state),
                    number(server, "online_players"));
        }
        gauge(out, "rexlcloud_servers", "Servers by group and state (waiting, started, online, stopping)");
        perGroupAndState.forEach((key, count) -> {
            var parts = key.split("\n", 2);
            sample(out, "rexlcloud_servers", Map.of("group", parts[0], "state", parts[1]), count);
        });

        var wrappers = api.getWrappers();
        gauge(out, "rexlcloud_wrapper_connected", "1 if the wrapper is connected");
        for (var element : wrappers) {
            var wrapper = element.getAsJsonObject();
            sample(out, "rexlcloud_wrapper_connected", Map.of("wrapper", text(wrapper, "name")),
                    wrapper.has("connected") && wrapper.get("connected").getAsBoolean() ? 1 : 0);
        }
        wrapperGauge(out, wrappers, "rexlcloud_wrapper_cpu_load", "CPU load of the wrapper's machine, 0 to 1", "cpu_usage");
        wrapperGauge(out, wrappers, "rexlcloud_wrapper_memory_free_bytes", "Memory new servers can use", "free_memory");
        wrapperGauge(out, wrappers, "rexlcloud_wrapper_memory_used_bytes", "Memory in use", "used_memory");
        wrapperGauge(out, wrappers, "rexlcloud_wrapper_memory_total_bytes", "Memory of the machine", "total_memory");
        return out.toString();
    }

    private static void wrapperGauge(StringBuilder out, Iterable<JsonElement> wrappers, String name, String help,
                                     String field) {
        gauge(out, name, help);
        for (var element : wrappers) {
            var wrapper = element.getAsJsonObject();
            // Only connected wrappers report values
            if (wrapper.has(field) && !wrapper.get(field).isJsonNull()) {
                sample(out, name, Map.of("wrapper", text(wrapper, "name")), wrapper.get(field).getAsDouble());
            }
        }
    }

    private static void gauge(StringBuilder out, String name, String help) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(" gauge\n");
    }

    private static void sample(StringBuilder out, String name, Map<String, String> labels, double value) {
        out.append(name);
        if (!labels.isEmpty()) {
            out.append('{');
            var first = true;
            for (var label : new TreeMap<>(labels).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(label.getKey()).append("=\"").append(escape(label.getValue())).append('"');
            }
            out.append('}');
        }
        out.append(' ');
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            out.append((long) value);
        } else {
            out.append(value);
        }
        out.append('\n');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String text(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
    }

    private static double number(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsDouble() : 0;
    }

}
