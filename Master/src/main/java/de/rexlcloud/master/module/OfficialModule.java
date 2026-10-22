package de.rexlcloud.master.module;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A module that ships with the cloud: attached to every GitHub release as {@code <artifact>-<version>.jar},
 * offered by the first-start setup and by {@code modules install}, and kept at the master's version by
 * {@link ModuleUpdater} once installed.
 * <p>
 * To ship a new optional module, add its jar to the release assets in {@code .github/workflows/release.yml} (like
 * the RestModule) and add it to {@link #ALL}.
 *
 * @param name               name in the module's module.json
 * @param artifact           name of its jar in the GitHub releases, without {@code -<version>.jar}
 * @param description        one line for the setup and {@code modules available}
 * @param installedByDefault the answer the setup suggests
 */
public record OfficialModule(String name, String artifact, String description, boolean installedByDefault) {

    public static final List<OfficialModule> ALL = List.of(
            new OfficialModule("RestModule", "restmodule",
                    "REST API for the web panel (web.rexlcloud.de) and for your own tools", true)
    );

    public static Optional<OfficialModule> byName(String name) {
        var lower = name.toLowerCase(Locale.ROOT);
        return ALL.stream()
                .filter(module -> module.name.toLowerCase(Locale.ROOT).equals(lower)
                        || module.artifact.equals(lower))
                .findFirst();
    }

}
