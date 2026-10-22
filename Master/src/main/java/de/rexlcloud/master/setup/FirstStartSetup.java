package de.rexlcloud.master.setup;

import de.rexlcloud.core.database.DatabaseCredentials;
import de.rexlcloud.core.server.ProxyForwarding;
import de.rexlcloud.master.module.OfficialModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The questions of the master's first start: the port for the wrappers, the proxy software (forwarding mode), the
 * database (H2, or MySQL/MariaDB set up and tested right here) and the optional official modules.
 * <p>
 * Without console input (e.g. stdin closed) every remaining question takes its suggested answer, so an unattended
 * first start still comes up with a working configuration.
 */
public class FirstStartSetup {

    /**
     * Where the questions go: the console, or scripted answers in tests.
     */
    public interface Io {
        /**
         * @return the answer, {@code defaultAnswer} for an empty one, or {@code null} if there is no input
         */
        String ask(String question, String defaultAnswer, boolean hidden, List<String> choices);

        void say(String line);
    }

    /**
     * Connects to a MySQL/MariaDB database (creating it if the user may), returning {@code null} if that worked or
     * the error otherwise.
     */
    public interface DatabaseCheck extends Function<DatabaseCredentials, String> {
    }

    /**
     * The answers. {@code database} is {@code null} if it was not asked (database.json already existed).
     */
    public record Result(int port, ProxyForwarding.Mode forwardingMode, DatabaseCredentials database,
                         List<OfficialModule> modules) {
    }

    /**
     * Database and user names: no characters that would change the JDBC URL.
     */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_$-]{1,64}");
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.:\\[\\]-]{1,253}");

    private final Io io;
    private final DatabaseCheck databaseCheck;
    /**
     * Set once the input ended; every later question takes its default.
     */
    private boolean noInput;

    public FirstStartSetup(Io io, DatabaseCheck databaseCheck) {
        this.io = io;
        this.databaseCheck = databaseCheck;
    }

    /**
     * @param askDatabase    whether to ask for the database (not if config/database.json exists)
     * @param offeredModules the official modules that are not installed yet
     */
    public Result run(boolean askDatabase, List<OfficialModule> offeredModules) {
        io.say("");
        io.say("Welcome to rexlCloud! A few questions set up the master.");
        io.say("Press Enter for the answer in [brackets], Tab shows the choices. Everything can be changed later in config/.");
        io.say("");

        var port = askNumber("Port the wrappers connect to", 1234, 1, 65535);

        io.say("Velocity is recommended: with modern forwarding nobody can join a server without the proxy.");
        var proxy = askChoice("Which proxy software will your network use?", "velocity", "velocity", "bungeecord");
        var forwarding = proxy.equals("bungeecord") ? ProxyForwarding.Mode.LEGACY : ProxyForwarding.Mode.MODERN;
        if (forwarding == ProxyForwarding.Mode.LEGACY) {
            io.say("BungeeCord uses legacy forwarding: block the server ports (25700 and up) in your firewall, so "
                    + "players can only join through the proxy.");
        }

        var database = askDatabase ? askDatabase() : null;

        var modules = new ArrayList<OfficialModule>();
        for (var module : offeredModules) {
            io.say(module.name() + ": " + module.description());
            if (askYesNo("Install the " + module.name() + "?", module.installedByDefault())) {
                modules.add(module);
            }
        }
        if (noInput) {
            io.say("No console input: the setup used the suggested answers.");
        }
        return new Result(port, forwarding, database, List.copyOf(modules));
    }

    private DatabaseCredentials askDatabase() {
        io.say("H2 is built in and needs nothing else; MySQL or MariaDB suit larger networks or an existing "
                + "database server.");
        var type = askChoice("Which database should the cloud use?", DatabaseCredentials.H2,
                DatabaseCredentials.H2, DatabaseCredentials.MYSQL, DatabaseCredentials.MARIADB);
        if (type.equals(DatabaseCredentials.H2)) {
            return DatabaseCredentials.embedded();
        }
        var name = type.equals(DatabaseCredentials.MARIADB) ? "MariaDB" : "MySQL";
        var credentials = askServerDatabase(name, new DatabaseCredentials(type, "127.0.0.1", 3306, "rexlcloud", "",
                "rexlcloud"));
        while (credentials != null) {
            io.say("Connecting to " + name + " at " + credentials.getHost() + ":" + credentials.getPort() + "...");
            var error = databaseCheck.apply(credentials);
            if (error == null) {
                io.say("Connected to the database " + credentials.getDatabase() + ".");
                return credentials;
            }
            io.say("The connection failed: " + error);
            if (noInput) {
                break;
            }
            var next = askChoice("Try again, change the settings, or use the built-in H2 database?", "change",
                    "retry", "change", "h2");
            if (next.equals("h2")) {
                return DatabaseCredentials.embedded();
            }
            if (next.equals("change")) {
                credentials = askServerDatabase(name, credentials);
            }
        }
        io.say("Using the built-in H2 database instead.");
        return DatabaseCredentials.embedded();
    }

    /**
     * @param previous the last answers, suggested again
     * @return the credentials, or {@code null} if there is no input to ask them
     */
    private DatabaseCredentials askServerDatabase(String name, DatabaseCredentials previous) {
        var host = askMatching(name + " host", previous.getHost(), HOST, "a host name or IP address");
        var port = askNumber(name + " port", previous.getPort(), 1, 65535);
        var database = askMatching("Database name (created if it does not exist and the user may)",
                previous.getDatabase(), NAME, "letters, numbers, _, - and $");
        var user = askMatching("Database user", previous.getUser(), NAME, "letters, numbers, _, - and $");
        String password;
        while (true) {
            var keep = previous.getPassword().isEmpty() ? "" : " (Enter keeps the last one)";
            password = ask("Password of " + user + keep, previous.getPassword(), true, List.of());
            if (password == null) {
                // No input: there is no password to try
                return null;
            }
            if (!password.isEmpty()) {
                break;
            }
            io.say("Please enter the password.");
        }
        return new DatabaseCredentials(previous.getType(), host, port, user, password, database);
    }

    private String ask(String question, String defaultAnswer, boolean hidden, List<String> choices) {
        if (noInput) {
            return defaultAnswer;
        }
        var answer = io.ask(question, defaultAnswer, hidden, choices);
        if (answer == null) {
            noInput = true;
            return defaultAnswer;
        }
        return answer;
    }

    String askChoice(String question, String defaultAnswer, String... choices) {
        var list = List.of(choices);
        while (true) {
            var answer = ask(question + " (" + String.join(", ", list) + ")", defaultAnswer, false, list)
                    .toLowerCase(Locale.ROOT);
            for (var choice : list) {
                if (choice.equals(answer) || (!answer.isEmpty() && choice.startsWith(answer))) {
                    return choice;
                }
            }
            io.say("Please answer with one of: " + String.join(", ", list));
        }
    }

    boolean askYesNo(String question, boolean defaultAnswer) {
        return askChoice(question, defaultAnswer ? "yes" : "no", "yes", "no").equals("yes");
    }

    int askNumber(String question, int defaultAnswer, int min, int max) {
        while (true) {
            var answer = ask(question, String.valueOf(defaultAnswer), false, List.of());
            try {
                var number = Integer.parseInt(answer);
                if (number >= min && number <= max) {
                    return number;
                }
            } catch (NumberFormatException ignored) {
                // asked again
            }
            io.say("Please enter a number from " + min + " to " + max + ".");
        }
    }

    private String askMatching(String question, String defaultAnswer, Pattern pattern, String allowed) {
        while (true) {
            var answer = ask(question, defaultAnswer, false, List.of());
            if (pattern.matcher(answer).matches()) {
                return answer;
            }
            io.say("Only " + allowed + ", please.");
        }
    }

}
