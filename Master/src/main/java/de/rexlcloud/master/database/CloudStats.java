package de.rexlcloud.master.database;

import de.rexlcloud.core.database.anotations.DatabaseData;
import de.rexlcloud.core.database.anotations.DatabaseValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@DatabaseData(tableName = "cloud_stats")
public class CloudStats {

    @DatabaseValue(id = true)
    private int id;
    @DatabaseValue
    private Timestamp timestamp;
    @DatabaseValue(valueName = "cpu_usage")
    private double cpuUsage;
    @DatabaseValue(valueName = "used_memory")
    private long usedMemory;
    @DatabaseValue(valueName = "free_memory")
    private long freeMemory;
    @DatabaseValue(valueName = "online_servers")
    private int onlineServerCount;
    @DatabaseValue(valueName = "started_servers")
    private int startedServerCount;
    @DatabaseValue(valueName = "online_players")
    private int onlinePlayerCount;

}
