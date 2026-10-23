package de.rexlcloud.master.player;

import de.rexlcloud.core.api.PlayerDatabaseData;
import de.rexlcloud.core.database.DatabaseObjectHandler;
import de.rexlcloud.core.database.helper.QueryFilter;
import de.rexlcloud.core.database.helper.QueryType;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The data plugins store for players (table {@code cloud_player_data}, one row per player and key). Reads and
 * writes run one after another, so a value saved while a player leaves is there when they join again, and two
 * servers setting the same new key do not create two rows.
 */
public class PlayerDataStore {

    private final DatabaseObjectHandler database;
    private CompletableFuture<?> last = CompletableFuture.completedFuture(null);

    public PlayerDataStore(DatabaseObjectHandler database) {
        this.database = database;
    }

    /**
     * @return all values stored for the player
     */
    public CompletableFuture<ArrayList<PlayerDatabaseData>> load(UUID uuid) {
        return queue(() -> database.getDataFromClass(PlayerDatabaseData.class, byPlayer(uuid)));
    }

    /**
     * Saves a value, replacing the one with the same key.
     *
     * @param value the serialized value, {@code null} to remove it
     */
    public CompletableFuture<Void> save(UUID uuid, String key, byte[] value) {
        return queue(() -> {
            var filters = new QueryFilter[]{byPlayer(uuid), new QueryFilter(QueryType.WHERE_EQUALS_TO, "data_key", key)};
            if (value == null) {
                return database.deleteDataFromClass(PlayerDatabaseData.class, filters);
            }
            return database.getDataFromClass(PlayerDatabaseData.class, filters).thenCompose(rows -> {
                if (rows.isEmpty()) {
                    return database.insertObject(new PlayerDatabaseData(uuid, key, value)).thenAccept(saved -> {
                    });
                }
                var row = rows.getFirst();
                row.setValue(value);
                return database.updateObject(row).thenAccept(saved -> {
                });
            });
        });
    }

    private static QueryFilter byPlayer(UUID uuid) {
        return new QueryFilter(QueryType.WHERE_EQUALS_TO, "unique_id", uuid);
    }

    /**
     * Runs the step after all earlier ones finished, whether they failed or not.
     */
    private synchronized <T> CompletableFuture<T> queue(Supplier<CompletableFuture<T>> step) {
        var next = last.handle((result, error) -> null).thenCompose(ignored -> {
            try {
                return step.get();
            } catch (RuntimeException e) {
                return CompletableFuture.<T>failedFuture(e);
            }
        });
        last = next;
        return next;
    }

}
