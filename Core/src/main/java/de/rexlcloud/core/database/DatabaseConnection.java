package de.rexlcloud.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Connection pool to the MySQL/MariaDB or embedded H2 database. Statements run on virtual threads; every returned future
 * completes, exceptionally if the database is not reachable.
 */
public class DatabaseConnection {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    @Nullable
    private final DatabaseCredentials credentials;
    @Getter
    @Nullable
    private DataSource dataSource;
    @Getter
    private final DatabaseObjectHandler objectHandler;

    public DatabaseConnection(DatabaseCredentials credentials) {
        this.credentials = credentials;
        objectHandler = new DatabaseObjectHandler(this);
    }

    /**
     * Uses an existing data source instead of connecting with credentials, e.g. in tests.
     */
    public DatabaseConnection(DataSource dataSource) {
        this.credentials = null;
        this.dataSource = dataSource;
        objectHandler = new DatabaseObjectHandler(this);
    }

    public void open() {
        if (credentials == null) {
            return;
        }
        try {
            this.dataSource = new HikariDataSource(createConfig(credentials));
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not connect to database\n\tDatabase credentials wrong?\n\tNo database bound to port?");
        }
    }

    private static HikariConfig createConfig(DatabaseCredentials credentials) {
        var config = new HikariConfig();
        config.setPoolName("rexlCloud-database");
        config.setJdbcUrl(credentials.jdbcUrl());
        if (credentials.isEmbedded()) {
            // One process uses the files; H2 finds its driver from the URL
            config.setMaximumPoolSize(4);
        } else {
            config.setUsername(credentials.getUser());
            config.setPassword(credentials.getPassword());
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }
        return config;
    }

    public void close() {
        executor.shutdown();
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @FunctionalInterface
    private interface SqlTask<R> {
        R run(Connection connection) throws SQLException;
    }

    private <R> CompletableFuture<R> submit(SqlTask<R> task) {
        var source = dataSource;
        if (source == null) {
            return CompletableFuture.failedFuture(new SQLException("There is no database connection"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try (var connection = source.getConnection()) {
                return task.run(connection);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    private static void bind(PreparedStatement statement, List<?> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    // Statements used by the object handler

    CompletableFuture<Integer> update(TableMapping.Query query) {
        return submit(connection -> {
            try (var statement = connection.prepareStatement(query.sql())) {
                bind(statement, query.parameters());
                return statement.executeUpdate();
            }
        });
    }

    /**
     * Runs an INSERT and returns the generated key, or {@code null} if there is none.
     */
    CompletableFuture<Object> insert(TableMapping.Query query) {
        return submit(connection -> {
            try (var statement = connection.prepareStatement(query.sql(), Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, query.parameters());
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getObject(1) : null;
                }
            }
        });
    }

    /**
     * Runs a SELECT and returns its rows, each with one value per column.
     */
    CompletableFuture<List<Object[]>> query(TableMapping.Query query) {
        return submit(connection -> {
            try (var statement = connection.prepareStatement(query.sql())) {
                bind(statement, query.parameters());
                try (var resultSet = statement.executeQuery()) {
                    var columns = resultSet.getMetaData().getColumnCount();
                    var rows = new ArrayList<Object[]>();
                    while (resultSet.next()) {
                        var row = new Object[columns];
                        for (int i = 0; i < columns; i++) {
                            row[i] = resultSet.getObject(i + 1);
                        }
                        rows.add(row);
                    }
                    return rows;
                }
            }
        });
    }

    // Plain SQL

    public CompletableFuture<Boolean> execute(@Language("SQL") String query) {
        return submit(connection -> {
            try (var statement = connection.createStatement()) {
                return statement.execute(query);
            }
        });
    }

    public CompletableFuture<Boolean> execute(@Language("SQL") String query, Object... parameters) {
        return submit(connection -> {
            try (var statement = connection.prepareStatement(query)) {
                bind(statement, Arrays.asList(parameters));
                return statement.execute();
            }
        });
    }

    /**
     * Runs an INSERT/UPDATE and returns the first {@code keys} generated keys of each row.
     */
    public CompletableFuture<Object[]> executeAndGet(@Language("SQL") String query, int keys, Object... parameters) {
        return submit(connection -> {
            try (var statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, Arrays.asList(parameters));
                statement.executeUpdate();
                try (var resultSet = statement.getGeneratedKeys()) {
                    var objects = new ArrayList<>();
                    while (resultSet.next()) {
                        for (int i = 0; i < keys; i++) {
                            objects.add(resultSet.getObject(i + 1));
                        }
                    }
                    return objects.toArray();
                }
            }
        });
    }

    /**
     * Runs a SELECT and returns the values of the given columns, row after row.
     */
    public CompletableFuture<Object[]> get(@Language("SQL") String query, String[] labels, Object... parameters) {
        return submit(connection -> {
            try (var statement = connection.prepareStatement(query)) {
                bind(statement, Arrays.asList(parameters));
                try (var resultSet = statement.executeQuery()) {
                    var objects = new ArrayList<>();
                    while (resultSet.next()) {
                        for (var label : labels) {
                            objects.add(resultSet.getObject(label));
                        }
                    }
                    return objects.toArray();
                }
            }
        });
    }

    public CompletableFuture<Object[]> get(@Language("SQL") String query, String[] labels) {
        return get(query, labels, new Object[0]);
    }

}
