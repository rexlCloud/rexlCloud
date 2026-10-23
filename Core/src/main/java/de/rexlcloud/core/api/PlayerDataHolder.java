package de.rexlcloud.core.api;

import lombok.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The data plugins store for a player in the cloud's database: an online {@link Player} and a
 * {@link CloudOfflinePlayer} read and change the same values. Changes are saved by the master and reach every server
 * the player is cached on.
 */
public abstract class PlayerDataHolder implements Serializable {

    private static final StackWalker CALLER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * @return the player's name
     */
    public abstract String getName();

    /**
     * @return the player's UUID
     */
    public abstract UUID getUuid();

    /**
     * @return the stored data entries of the player; read them with {@link #getData(String)}
     */
    public abstract ArrayList<PlayerDatabaseData> getData();

    /**
     * Data a plugin stored for this player. Only JDK value types and classes of the calling plugin are read; data
     * of other types is treated as missing.
     */
    @SuppressWarnings("unchecked")
    public <T extends Serializable> Optional<T> getData(String key) {
        var caller = CALLER.getCallerClass().getClassLoader();
        return getData().stream().filter(playerDatabaseData -> playerDatabaseData.getKey().equals(key)).findAny()
                .map(playerDatabaseData -> {
                    try {
                        return (T) SafeObjectInput.read(playerDatabaseData.getValue(), caller);
                    } catch (IOException | ClassNotFoundException | ClassCastException e) {
                        System.err.println("[rexlCloud] Could not read the player data " + key + " of " + getName()
                                + ": " + e.getMessage());
                        return null;
                    }
                });
    }

    /**
     * Stores data for the player in the cloud's database, replacing a value with the same key. The value is
     * serialized with Java serialization; only JDK value types (strings, numbers, collections, time types) and classes of
     * the plugin itself can be read back with {@link #getData(String)}.
     *
     * @param key   name of the value, e.g. {@code "coins"}
     * @param value the value
     * @param <T>   type of the value
     * @return {@code false} if the value could not be serialized
     */
    public <T extends Serializable> boolean setData(@NonNull String key, @NonNull T value) {
        byte[] bytes;
        try {
            var baos = new ByteArrayOutputStream();
            var oos = new ObjectOutputStream(baos);
            oos.writeObject(value);
            oos.flush();
            bytes = baos.toByteArray();
        } catch (IOException e) {
            System.err.println("[rexlCloud] Could not serialize the player data " + key + " of " + getName()
                    + ": " + e.getMessage());
            return false;
        }
        updateData(key, bytes);
        RexlCloudApi.getInstance().sendPlayerDataUpdate(this, key, bytes);
        return true;
    }

    /**
     * @param key name of a value
     * @return whether the player has data under this key
     */
    public boolean hasData(String key) {
        return getData().stream().anyMatch(playerDatabaseData -> playerDatabaseData.getKey().equals(key));
    }

    /**
     * @return the names of all values stored for the player
     */
    public List<String> getDataKeys() {
        return getData().stream().map(PlayerDatabaseData::getKey).toList();
    }

    /**
     * Removes the player's data under this key from the database.
     *
     * @param key name of the value
     */
    public void removeData(String key) {
        updateData(key, null);
        RexlCloudApi.getInstance().sendPlayerDataUpdate(this, key, null);
    }

    /**
     * Internal: applies a changed value ({@code null} removes it) without saving it.
     */
    public void updateData(String key, byte[] data) {
        var entries = getData();
        if (data == null) {
            entries.removeIf(playerDatabaseData -> playerDatabaseData.getKey().equals(key));
            return;
        }
        entries.stream().filter(playerDatabaseData -> playerDatabaseData.getKey().equals(key)).findAny()
                .ifPresentOrElse(playerDatabaseData -> playerDatabaseData.setValue(data),
                        () -> entries.add(new PlayerDatabaseData(getUuid(), key, data)));
    }

}
