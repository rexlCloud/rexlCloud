package de.rexlcloud.core.database;

import de.rexlcloud.core.database.helper.QueryFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Stores objects of classes annotated with {@link de.rexlcloud.core.database.anotations.DatabaseData} in tables,
 * one column per {@link de.rexlcloud.core.database.anotations.DatabaseValue} field.
 */
public class DatabaseObjectHandler {

    private final Map<Class<?>, TableMapping<?>> mappings = new ConcurrentHashMap<>();
    private final DatabaseConnection databaseConnection;

    protected DatabaseObjectHandler(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    @SuppressWarnings("unchecked")
    private <T> TableMapping<T> mapping(Class<T> clazz) {
        return (TableMapping<T>) mappings.computeIfAbsent(clazz, TableMapping::new);
    }

    /**
     * Runs a step that may fail before any statement is sent (e.g. an invalid filter) as a failed future.
     */
    private static <R> CompletableFuture<R> attempt(Supplier<CompletableFuture<R>> step) {
        try {
            return step.get();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Boolean> createTable(Class<?> clazz) {
        return attempt(() -> databaseConnection.update(new TableMapping.Query(mapping(clazz).createTable(), List.of()))
                .thenApply(changed -> true));
    }

    public CompletableFuture<Boolean> createTable(Object object) {
        return createTable(object.getClass());
    }

    public CompletableFuture<Boolean> dropTable(Class<?> clazz) {
        return attempt(() -> {
            var drop = mapping(clazz).dropTable();
            mappings.remove(clazz);
            return databaseConnection.update(new TableMapping.Query(drop, List.of())).thenApply(changed -> true);
        });
    }

    public CompletableFuture<Boolean> dropTable(Object object) {
        return dropTable(object.getClass());
    }

    public <T> CompletableFuture<ArrayList<T>> getAllObjects(Class<T> clazz) {
        return getDataFromClass(clazz);
    }

    /**
     * @return the object, or a future failing if there is none
     */
    public <T> CompletableFuture<T> getObjectWithID(Class<T> clazz, Number id) {
        return attempt(() -> {
            var mapping = mapping(clazz);
            return databaseConnection.query(mapping.selectById(id)).thenApply(rows -> {
                if (rows.isEmpty()) {
                    throw new IllegalStateException("There is no " + clazz.getName() + " with id " + id);
                }
                return mapping.createObject(rows.getFirst());
            });
        });
    }

    public CompletableFuture<Boolean> deleteObject(Object object) {
        return attempt(() -> databaseConnection.update(mapping(object.getClass()).deleteObject(object))
                .thenApply(deleted -> deleted > 0));
    }

    public CompletableFuture<Boolean> updateObject(Object object) {
        return attempt(() -> databaseConnection.update(mapping(object.getClass()).update(object))
                .thenApply(updated -> updated > 0));
    }

    /**
     * Inserts the object. A generated id is set on the object before the future completes.
     */
    public CompletableFuture<Boolean> insertObject(Object object) {
        return attempt(() -> {
            var mapping = mapping(object.getClass());
            return databaseConnection.insert(mapping.insert(object)).thenApply(key -> {
                if (mapping.hasGeneratedId()) {
                    if (key == null) {
                        return false;
                    }
                    mapping.setGeneratedId(object, key);
                }
                return true;
            });
        });
    }

    /**
     * Deletes the rows matching the filters. At least one WHERE filter is required.
     */
    public <T> CompletableFuture<Void> deleteDataFromClass(Class<T> clazz, QueryFilter... filter) {
        return attempt(() -> databaseConnection.update(mapping(clazz).delete(filter)).thenAccept(deleted -> {
        }));
    }

    public <T> CompletableFuture<ArrayList<T>> getDataFromClass(Class<T> clazz, QueryFilter... filter) {
        return attempt(() -> {
            var mapping = mapping(clazz);
            return databaseConnection.query(mapping.select(filter)).thenApply(rows -> {
                var objects = new ArrayList<T>(rows.size());
                rows.forEach(row -> objects.add(mapping.createObject(row)));
                return objects;
            });
        });
    }

}
