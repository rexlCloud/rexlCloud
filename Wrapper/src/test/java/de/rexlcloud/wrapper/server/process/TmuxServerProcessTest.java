package de.rexlcloud.wrapper.server.process;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TmuxServerProcessTest {

    @Test
    void theStartScriptQuotesEveryArgument() {
        var script = TmuxServerProcess.script(List.of("/opt/my java/bin/java", "-Dname=it's", "-jar", "server.jar"));

        assertTrue(script.startsWith("#!/bin/bash\n"));
        assertTrue(script.contains("'/opt/my java/bin/java' '-Dname=it'\\''s' '-jar' 'server.jar' 2>&1 | tee -a .rexlcloud-console.log"),
                script);
        assertTrue(script.contains("echo \"${PIPESTATUS[0]}\" > .rexlcloud-exit"), "the exit code of Java, not of tee");
    }

}
