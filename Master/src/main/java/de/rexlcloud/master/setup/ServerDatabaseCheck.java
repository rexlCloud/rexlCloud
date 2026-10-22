package de.rexlcloud.master.setup;

import de.rexlcloud.core.database.DatabaseCredentials;

import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Connects to MySQL or MariaDB with the setup's answers and creates the database if it does not exist (when the
 * user may).
 */
public class ServerDatabaseCheck implements FirstStartSetup.DatabaseCheck {

    @Override
    public String apply(DatabaseCredentials credentials) {
        var url = "jdbc:mysql://" + credentials.getHost() + ":" + credentials.getPort() + "/" + credentials.getDatabase()
                + "?createDatabaseIfNotExist=true&connectTimeout=5000&socketTimeout=10000";
        var properties = new Properties();
        properties.setProperty("user", credentials.getUser());
        properties.setProperty("password", credentials.getPassword());
        Driver driver;
        try {
            // The driver directly: DriverManager does not see drivers loaded by the launcher's class loader
            driver = (Driver) Class.forName("com.mysql.cj.jdbc.Driver", true, getClass().getClassLoader())
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return "the MySQL driver is missing: " + e.getMessage();
        }
        try (var connection = driver.connect(url, properties);
             var statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return null;
        } catch (SQLException e) {
            return firstLine(e.getMessage());
        }
    }

    /**
     * The driver's messages can span many lines (e.g. the last packet sent); the first says what went wrong.
     */
    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        var newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline).trim();
    }

}
