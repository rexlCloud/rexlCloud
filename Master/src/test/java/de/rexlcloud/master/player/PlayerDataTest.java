package de.rexlcloud.master.player;

import de.rexlcloud.core.api.PlayerDatabaseData;
import de.rexlcloud.core.database.DatabaseConnection;
import de.rexlcloud.core.database.DatabaseObjectHandler;
import de.rexlcloud.master.database.PlayerData;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Player data saved by the master and read back for offline players, on an in-memory H2 database in MySQL mode.
 */
class PlayerDataTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID JEB = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

    private DatabaseConnection connection;
    private DatabaseObjectHandler database;
    private PlayerManager players;

    @BeforeEach
    void setUp() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        connection = new DatabaseConnection(dataSource);
        database = connection.getObjectHandler();
        database.createTable(PlayerData.class).join();
        database.createTable(PlayerDatabaseData.class).join();
        players = new PlayerManager(database);
    }

    @AfterEach
    void tearDown() {
        connection.close();
    }

    private static byte[] serialize(Serializable value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private void joined(UUID uuid, String name, long firstLogin, long lastLogin) {
        database.insertObject(new PlayerData(0, name, uuid, new Timestamp(firstLogin), new Timestamp(lastLogin), false)).join();
    }

    @Test
    void valuesAreSavedReplacedAndRemoved() throws IOException {
        var store = players.getDataStore();
        store.save(NOTCH, "coins", serialize(100)).join();
        store.save(NOTCH, "rank", serialize("VIP")).join();
        store.save(NOTCH, "coins", serialize(250)).join();
        store.save(JEB, "coins", serialize(5)).join();

        var notch = store.load(NOTCH).join();
        assertEquals(2, notch.size());
        var coins = notch.stream().filter(entry -> entry.getKey().equals("coins")).toList();
        assertEquals(1, coins.size(), "replacing a value must not add a row");
        assertArrayEquals(serialize(250), coins.getFirst().getValue());

        store.save(NOTCH, "rank", null).join();
        assertEquals(List.of("coins"), store.load(NOTCH).join().stream().map(PlayerDatabaseData::getKey).toList());
        assertEquals(1, store.load(JEB).join().size(), "other players keep their data");
    }

    @Test
    void writesOfTheSameNewKeyRunOneAfterAnother() throws IOException {
        var store = players.getDataStore();
        var saves = new ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < 20; i++) {
            saves.add(store.save(NOTCH, "coins", serialize(i)));
        }
        CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new)).join();

        var rows = store.load(NOTCH).join();
        assertEquals(1, rows.size());
        assertArrayEquals(serialize(19), rows.getFirst().getValue(), "the last write wins");
    }

    @Test
    void offlinePlayersByUuidAndNameWithTheirData() throws IOException {
        joined(NOTCH, "Notch", 1_000L, 5_000L);
        players.getDataStore().save(NOTCH, "coins", serialize(250)).join();

        var byUuid = players.getOfflinePlayer(NOTCH).join().orElseThrow();
        assertEquals("Notch", byUuid.getName());
        assertEquals(1_000L, byUuid.getFirstLogin());
        assertEquals(5_000L, byUuid.getLastLogin());
        assertFalse(byUuid.isOnline());
        assertEquals(250, byUuid.<Integer>getData("coins").orElseThrow());

        var byName = players.getOfflinePlayer("nOTCH").join().orElseThrow();
        assertEquals(NOTCH, byName.getUuid(), "names are found ignoring case");
    }

    @Test
    void aNameTakenOverByAnotherPlayerFindsTheOneWhoJoinedLast() {
        joined(NOTCH, "Steve", 1_000L, 2_000L);
        joined(JEB, "Steve", 3_000L, 4_000L);

        assertEquals(JEB, players.getOfflinePlayer("Steve").join().orElseThrow().getUuid());
    }

    @Test
    void playersWhoNeverJoinedAreNotFound() {
        assertTrue(players.getOfflinePlayer(NOTCH).join().isEmpty());
        assertTrue(players.getOfflinePlayer("Notch").join().isEmpty());
    }

}
