package de.rexlcloud.restmodule.config;

import de.rexlcloud.core.config.JsonConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RestConfigTest {

    @TempDir
    Path folder;

    @Test
    void httpsStaysOffWhenTheConfigIsSavedAndLoadedAgain() throws Exception {
        var file = folder.resolve("config.json");
        Files.writeString(file, "{\"host\":\"0.0.0.0\",\"port\":4343}");
        for (var run = 0; run < 3; run++) {
            var config = JsonConfiguration.loadOrCreateConfiguration(file, new RestConfig());
            assertFalse(config.getTls().enabled(), "run " + run + ": " + Files.readString(file));
            JsonConfiguration.saveConfiguration(file, config);
        }
    }

}
