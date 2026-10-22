package de.rexlcloud.core.database;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.Locale;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DatabaseCredentials {

    /**
     * MySQL or MariaDB at {@link #host}.
     */
    public static final String MYSQL = "mysql";
    /**
     * MariaDB at {@link #host}; the same as {@link #MYSQL} (MariaDB speaks the MySQL protocol), only named after the
     * database the user chose.
     */
    public static final String MARIADB = "mariadb";
    /**
     * An H2 database in files below {@code database/} in the working folder; needs no database server.
     */
    public static final String H2 = "h2";

    /**
     * {@link #MYSQL}, {@link #MARIADB} or {@link #H2}; missing in configurations of older versions, which used
     * MySQL.
     */
    private String type;
    private String host = "localhost";
    private int port = 3306;
    private String user = "user";
    private String password = "password";
    private String database = "database";

    /**
     * For new installs: works without setting up a database server.
     */
    public static DatabaseCredentials embedded() {
        return new DatabaseCredentials(H2, "", 0, "", "", "rexlcloud");
    }

    public boolean isEmbedded() {
        return type != null && type.toLowerCase(Locale.ROOT).equals(H2);
    }

    /**
     * JDBC URL of the database; H2 runs in MySQL mode, so the same SQL works on both.
     */
    public String jdbcUrl() {
        if (isEmbedded()) {
            var file = Path.of("database").resolve(database).toAbsolutePath();
            return "jdbc:h2:file:" + file + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    @Override
    public String toString() {
        // Keeps the password out of logs
        var shownType = isEmbedded() ? H2 : type == null ? MYSQL : type;
        return "DatabaseCredentials{type='" + shownType + "', host='" + host + "', port=" + port
                + ", user='" + user + "', database='" + database + "'}";
    }

}
