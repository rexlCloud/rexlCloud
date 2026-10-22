package de.rexlcloud.master.setup;

import de.rexlcloud.core.database.DatabaseCredentials;
import de.rexlcloud.core.server.ProxyForwarding;
import de.rexlcloud.master.module.OfficialModule;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FirstStartSetupTest {

    private static final OfficialModule REST = OfficialModule.byName("RestModule").orElseThrow();

    /**
     * Answers questions in order; {@code null} ends the input. An empty answer takes the default, like the console.
     */
    private static class ScriptedIo implements FirstStartSetup.Io {
        final Deque<String> answers;
        final List<String> questions = new ArrayList<>();
        final List<String> said = new ArrayList<>();
        final List<String> hiddenDefaults = new ArrayList<>();

        ScriptedIo(String... answers) {
            this.answers = new ArrayDeque<>();
            // ArrayDeque refuses null, so the end of input is "no more answers"
            this.answers.addAll(Arrays.asList(answers));
        }

        @Override
        public String ask(String question, String defaultAnswer, boolean hidden, List<String> choices) {
            questions.add(question);
            if (hidden) {
                hiddenDefaults.add(defaultAnswer);
            }
            var answer = answers.poll();
            if (answer == null) {
                return null;
            }
            return answer.isEmpty() && defaultAnswer != null ? defaultAnswer : answer;
        }

        @Override
        public void say(String line) {
            said.add(line);
        }
    }

    private static FirstStartSetup.DatabaseCheck failing(String... errors) {
        var queue = new ArrayDeque<>(Arrays.asList(errors));
        // An empty queue means the connection works
        return credentials -> queue.poll();
    }

    @Test
    void emptyAnswersTakeTheSuggestions() {
        var io = new ScriptedIo("", "", "", "");

        var result = new FirstStartSetup(io, failing()).run(true, List.of(REST));

        assertEquals(1234, result.port());
        assertEquals(ProxyForwarding.Mode.MODERN, result.forwardingMode());
        assertTrue(result.database().isEmbedded());
        assertEquals(List.of(REST), result.modules());
        assertTrue(io.answers.isEmpty(), "exactly four questions");
    }

    @Test
    void withoutInputEverythingTakesItsDefault() {
        var io = new ScriptedIo();

        var result = new FirstStartSetup(io, failing()).run(true, List.of(REST));

        assertEquals(1234, result.port());
        assertTrue(result.database().isEmbedded());
        assertEquals(List.of(REST), result.modules());
        assertEquals(1, io.questions.size(), "nothing is asked after the input ended");
        assertTrue(io.said.stream().anyMatch(line -> line.contains("No console input")));
    }

    @Test
    void invalidAnswersAreAskedAgain() {
        var io = new ScriptedIo("abc", "70000", "2000", "paper", "b", "h2", "maybe", "n");

        var result = new FirstStartSetup(io, failing()).run(true, List.of(REST));

        assertEquals(2000, result.port());
        assertEquals(ProxyForwarding.Mode.LEGACY, result.forwardingMode(), "b is short for bungeecord");
        assertEquals(List.of(), result.modules());
        assertTrue(io.said.stream().anyMatch(line -> line.contains("firewall")), "BungeeCord warns about the ports");
    }

    @Test
    void mysqlIsAskedAndTestedAndCanBeRetried() {
        var io = new ScriptedIo("", "", "mysql", "db.local", "3307", "cloud", "cloud_user", "", "secret",
                "retry", "yes");

        var result = new FirstStartSetup(io, failing("Access denied")).run(true, List.of(REST));

        var database = result.database();
        assertEquals(DatabaseCredentials.MYSQL, database.getType());
        assertEquals("db.local", database.getHost());
        assertEquals(3307, database.getPort());
        assertEquals("cloud", database.getDatabase());
        assertEquals("cloud_user", database.getUser());
        assertEquals("secret", database.getPassword(), "an empty password is asked again");
        assertTrue(io.said.stream().anyMatch(line -> line.contains("Access denied")));
    }

    @Test
    void changingTheSettingsNeverShowsThePassword() {
        var io = new ScriptedIo("", "", "mariadb", "", "", "", "", "first", "change", "", "", "", "", "", "");

        var result = new FirstStartSetup(io, failing("wrong password")).run(true, List.of());

        assertEquals(DatabaseCredentials.MARIADB, result.database().getType());
        assertEquals("first", result.database().getPassword(), "Enter keeps the last password");
        assertEquals(Arrays.asList("", "first"), io.hiddenDefaults);
        assertTrue(io.questions.stream().noneMatch(question -> question.contains("first")));
    }

    @Test
    void aFailingDatabaseCanFallBackToH2() {
        var io = new ScriptedIo("", "", "mysql", "", "", "", "", "pw", "h2");

        var result = new FirstStartSetup(io, failing("Connection refused")).run(true, List.of());

        assertTrue(result.database().isEmbedded());
    }

    @Test
    void anExistingDatabaseConfigurationIsNotAsked() {
        var io = new ScriptedIo("", "");

        var result = new FirstStartSetup(io, failing()).run(false, List.of());

        assertNull(result.database());
        assertEquals(2, io.questions.size());
        assertTrue(io.questions.stream().noneMatch(question -> question.contains("database")));
    }

    @Test
    void invalidDatabaseNamesAreRefused() {
        var io = new ScriptedIo("", "", "mysql", "", "", "x;drop", "cloud", "", "pw");

        var result = new FirstStartSetup(io, failing()).run(true, List.of());

        assertEquals("cloud", result.database().getDatabase());
    }

}
