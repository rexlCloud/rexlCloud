package de.rexlcloud.master.database;

import de.rexlcloud.core.database.anotations.DatabaseData;
import de.rexlcloud.core.database.anotations.DatabaseValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@DatabaseData(tableName = "cloud_players")
public class PlayerData {

    @DatabaseValue(id = true)
    private int id;
    @Setter
    @DatabaseValue
    private String username;
    @DatabaseValue(valueName = "unique_id")
    private UUID uniqueId;
    @DatabaseValue(valueName = "first_login")
    private Timestamp firstLogin;
    @Setter
    @DatabaseValue(valueName = "last_login")
    private Timestamp lastLogin;
    @Setter
    @DatabaseValue(valueName = "banned")
    private boolean banned;

}
