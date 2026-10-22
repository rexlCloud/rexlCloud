package de.rexlcloud.core.api;

import de.rexlcloud.core.database.anotations.DatabaseData;
import de.rexlcloud.core.database.anotations.DatabaseValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * One value a plugin stored for a player (see {@link Player#setData(String, java.io.Serializable)}).
 */
@Getter
@NoArgsConstructor
@DatabaseData(tableName = "cloud_player_data")
public class PlayerDatabaseData implements Serializable {

    @DatabaseValue(id = true)
    private int id;

    /**
     * The player.
     */
    @DatabaseValue(valueName = "unique_id")
    private UUID uuid;

    /**
     * Name of the value.
     */
    @DatabaseValue(valueName = "data_key")
    private String key;

    /**
     * The serialized value; read it with {@link Player#getData(String)}.
     */
    @Setter
    @DatabaseValue(valueName = "data_value")
    private byte[] value;

    /**
     * Internal: created by {@link Player#setData(String, java.io.Serializable)}.
     */
    public PlayerDatabaseData(UUID uuid, String key, byte[] value) {
        this.uuid = uuid;
        this.key = key;
        this.value = value;
    }
}
