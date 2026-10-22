package de.rexlcloud.core.database;

import de.rexlcloud.core.database.anotations.DatabaseData;
import de.rexlcloud.core.database.anotations.DatabaseValue;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classes stored by the database tests.
 */
final class TestEntities {

    private TestEntities() {
    }

    enum Rank {
        PLAYER,
        // A constant with a body is a subclass of the enum
        ADMIN {
            @Override
            public String toString() {
                return "Administrator";
            }
        }
    }

    record Settings(boolean chat, int volume, List<String> friends) {
    }

    @DatabaseData(tableName = "test_everything")
    static class Everything {
        @DatabaseValue(id = true)
        int id;
        @DatabaseValue
        String name;
        @DatabaseValue(valueName = "big_text", length = 2000)
        String bigText;
        @DatabaseValue
        long counter;
        @DatabaseValue
        double ratio;
        @DatabaseValue
        boolean active;
        @DatabaseValue(valueName = "unique_id")
        UUID uniqueId;
        @DatabaseValue
        Rank rank;
        @DatabaseValue
        Timestamp time;
        @DatabaseValue
        Instant seen;
        @DatabaseValue
        byte[] payload;
        @DatabaseValue
        ArrayList<String> messages;
        @DatabaseValue
        List<UUID> friends;
        @DatabaseValue
        Map<String, Integer> scores;
        @DatabaseValue
        Settings settings;
        @DatabaseValue
        Integer optional;

        Everything() {
        }
    }

    /**
     * Same table as rexlCloud 2.0 created it: ENUM, TIMESTAMP and a Java-serialized LONGBLOB list.
     */
    @DatabaseData(tableName = "test_legacy")
    static class Legacy {
        @DatabaseValue(id = true)
        int id;
        @DatabaseValue
        Rank rank;
        @DatabaseValue
        Timestamp time;
        @DatabaseValue
        ArrayList<String> messages;
        @DatabaseValue
        List<Object> anything;
    }

    /**
     * Not one of the classes old rows may contain.
     */
    static class NotAllowed implements Serializable {
        String value = "x";
    }

    @DatabaseData(tableName = "bad name")
    static class BadTableName {
        @DatabaseValue(id = true)
        int id;
    }

}
