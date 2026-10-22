package de.rexlcloud.restmodule;

import de.rexlcloud.restmodule.user.Permission;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Runs the REST API on port 4343 with fake cloud data, for developing the web panel without a real cloud.
 * Log in with {@code dev} / {@code devpassword}. Start with {@code ./gradlew :RestModule:runDevServer}.
 */
public class DevServer {

    private static final List<String> LINES = List.of(
            "[INFO] Saving chunks for level 'world'",
            "\u001b[33m[WARN]\u001b[0m Can't keep up! Is the server overloaded?",
            "[INFO] Steve joined the game",
            "[INFO] <Alex> hello there",
            "\u001b[36m[INFO]\u001b[0m Autosave finished");

    public static void main(String[] args) throws Exception {
        var port = Integer.parseInt(System.getProperty("rexlcloud.devPort", "4343"));
        var api = new FakeModuleApi();
        TestCloud.start(Path.of("data"), "0.0.0.0", port, api);
        if (RestModule.getInstance().getUserManager().getUser("dev").isEmpty()) {
            TestCloud.createUser("dev", "devpassword", Permission.values());
        }

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            var random = ThreadLocalRandom.current();
            var servers = api.serverList();
            if (servers.isEmpty()) {
                return;
            }
            var server = servers.get(random.nextInt(servers.size()));
            server.console.add("[" + LocalTime.now().withNano(0) + "] " + LINES.get(random.nextInt(LINES.size())));
        }, 1, 1, TimeUnit.SECONDS);

        System.out.println("Dev REST API running on http://localhost:" + port + "/api/v2 (user dev / devpassword)");
        Thread.currentThread().join();
    }

}
