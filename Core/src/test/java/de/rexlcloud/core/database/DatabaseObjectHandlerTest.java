package de.rexlcloud.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.rexlcloud.core.database.TestEntities.Everything;
import de.rexlcloud.core.database.TestEntities.Legacy;
import de.rexlcloud.core.database.TestEntities.NotAllowed;
import de.rexlcloud.core.database.TestEntities.Rank;
import de.rexlcloud.core.database.TestEntities.Settings;
import de.rexlcloud.core.database.helper.QueryFilter;
import de.rexlcloud.core.database.helper.QueryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs against an in-memory H2 database in MySQL mode. Set REXLCLOUD_TEST_JDBC_URL (and _USER, _PASSWORD) to run
 * the same tests against a real MySQL/MariaDB; the tests create and drop their own tables.
 */
class DatabaseObjectHandlerTest {

    private HikariDataSource dataSource;
    private DatabaseConnection connection;
    private DatabaseObjectHandler handler;

    @BeforeEach
    void setUp() {
        var config = new HikariConfig();
        var url = System.getenv("REXLCLOUD_TEST_JDBC_URL");
        if (url == null) {
            config.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        } else {
            config.setJdbcUrl(url);
            config.setUsername(System.getenv("REXLCLOUD_TEST_JDBC_USER"));
            config.setPassword(System.getenv("REXLCLOUD_TEST_JDBC_PASSWORD"));
        }
        dataSource = new HikariDataSource(config);
        connection = new DatabaseConnection(dataSource);
        handler = connection.getObjectHandler();
        handler.dropTable(Everything.class).join();
        handler.dropTable(Legacy.class).join();
        handler.createTable(Everything.class).join();
    }

    @AfterEach
    void tearDown() {
        handler.dropTable(Everything.class).join();
        handler.dropTable(Legacy.class).join();
        connection.close();
    }

    private static Everything everything(String name, long counter) {
        var object = new Everything();
        object.name = name;
        object.bigText = "x".repeat(1500);
        object.counter = counter;
        object.ratio = 0.25;
        object.active = true;
        object.uniqueId = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        object.rank = Rank.ADMIN;
        object.time = new Timestamp(1_760_000_000_000L);
        object.seen = Instant.parse("2026-10-19T08:00:00.123Z");
        object.payload = new byte[]{1, 2, 3, (byte) 0xAC, (byte) 0xED};
        object.messages = new ArrayList<>(List.of("hello", "wörld \"quoted\""));
        object.friends = List.of(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"));
        object.scores = Map.of("bedwars", 3, "skywars", 7);
        object.settings = new Settings(true, 80, List.of("Steve"));
        object.optional = null;
        return object;
    }

    @Test
    void insertSetsTheGeneratedIdAndEveryTypeRoundTrips() {
        var stored = everything("Notch", 42);
        assertTrue(handler.insertObject(stored).join());
        assertTrue(stored.id > 0);

        var loaded = handler.getObjectWithID(Everything.class, stored.id).join();
        assertEquals(stored.id, loaded.id);
        assertEquals("Notch", loaded.name);
        assertEquals(stored.bigText, loaded.bigText);
        assertEquals(42, loaded.counter);
        assertEquals(0.25, loaded.ratio);
        assertTrue(loaded.active);
        assertEquals(stored.uniqueId, loaded.uniqueId);
        assertEquals(Rank.ADMIN, loaded.rank);
        assertEquals(stored.time, loaded.time);
        assertEquals(stored.seen.truncatedTo(ChronoUnit.MILLIS), loaded.seen);
        assertArrayEquals(stored.payload, loaded.payload);
        assertEquals(stored.messages, loaded.messages);
        assertEquals(ArrayList.class, loaded.messages.getClass());
        assertEquals(stored.friends, loaded.friends);
        assertEquals(UUID.class, loaded.friends.getFirst().getClass());
        assertEquals(stored.scores, loaded.scores);
        assertEquals(stored.settings, loaded.settings);
        assertNull(loaded.optional);
    }

    @Test
    void nullValuesBecomeDefaultsForPrimitives() {
        connection.execute("INSERT INTO `test_everything` (`name`) VALUES (?)", "only a name").join();

        var loaded = handler.getAllObjects(Everything.class).join().getFirst();
        assertEquals("only a name", loaded.name);
        assertEquals(0, loaded.counter);
        assertFalse(loaded.active);
        assertNull(loaded.rank);
        assertNull(loaded.messages);
    }

    @Test
    void filtersOrderAndLimit() {
        for (int i = 1; i <= 5; i++) {
            handler.insertObject(everything("player" + i, i * 10L)).join();
        }

        var topTwo = handler.getDataFromClass(Everything.class,
                new QueryFilter(QueryType.LIMIT, "", 2),
                new QueryFilter(QueryType.ORDER_BY_DESC, "counter", null)).join();
        assertEquals(List.of("player5", "player4"), topTwo.stream().map(e -> e.name).toList());

        var range = handler.getDataFromClass(Everything.class,
                new QueryFilter(QueryType.WHERE_MORE_THAN, "counter", 10L),
                new QueryFilter(QueryType.WHERE_LESS_OR_EQUALS_THAN, "counter", 40L),
                new QueryFilter(QueryType.ORDER_BY_ASC, "counter", null),
                new QueryFilter(QueryType.ORDER_BY_DESC, "name", null)).join();
        assertEquals(List.of(20L, 30L, 40L), range.stream().map(e -> e.counter).toList());

        var notThree = handler.getDataFromClass(Everything.class,
                new QueryFilter(QueryType.WHERE_MORE_OR_LESS_THAN, "name", "player3")).join();
        assertEquals(4, notThree.size());

        // Values are encoded like the column: a UUID and an enum constant
        assertEquals(5, handler.getDataFromClass(Everything.class,
                new QueryFilter(QueryType.WHERE_EQUALS_TO, "unique_id", UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")),
                new QueryFilter(QueryType.WHERE_EQUALS_TO, "rank", Rank.ADMIN)).join().size());
        assertEquals(5, handler.getDataFromClass(Everything.class,
                new QueryFilter(QueryType.WHERE_EQUALS_TO, "optional", null)).join().size());
    }

    @Test
    void updateAndDelete() {
        var object = everything("Steve", 1);
        handler.insertObject(object).join();
        handler.insertObject(everything("Alex", 2)).join();

        object.name = "Steve2";
        object.messages.add("new");
        assertTrue(handler.updateObject(object).join());
        var updated = handler.getObjectWithID(Everything.class, object.id).join();
        assertEquals("Steve2", updated.name);
        assertEquals(List.of("hello", "wörld \"quoted\"", "new"), updated.messages);

        assertTrue(handler.deleteObject(object).join());
        handler.deleteDataFromClass(Everything.class, new QueryFilter(QueryType.WHERE_EQUALS_TO, "name", "Alex")).join();
        assertTrue(handler.getAllObjects(Everything.class).join().isEmpty());

        var missing = assertThrows(CompletionException.class,
                () -> handler.getObjectWithID(Everything.class, object.id).join());
        assertTrue(missing.getCause().getMessage().contains("no"));
    }

    @Test
    void deletingWithoutWhereFilterIsRefused() {
        handler.insertObject(everything("Steve", 1)).join();

        assertThrows(CompletionException.class, () -> handler.deleteDataFromClass(Everything.class).join());
        assertThrows(CompletionException.class, () -> handler.deleteDataFromClass(Everything.class,
                new QueryFilter(QueryType.LIMIT, "", 1)).join());
        assertEquals(1, handler.getAllObjects(Everything.class).join().size());
    }

    @Test
    void unknownFilterColumnsAreRejected() {
        var failure = assertThrows(CompletionException.class, () -> handler.getDataFromClass(Everything.class,
                new QueryFilter(QueryType.WHERE_EQUALS_TO, "name = name OR 1", "x")).join());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    @Test
    void invalidTableNamesAreRejected() {
        var failure = assertThrows(CompletionException.class,
                () -> handler.createTable(TestEntities.BadTableName.class).join());
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    @Test
    void tablesOfOlderVersionsStillWork() throws IOException {
        // As rexlCloud 2.0 created it
        connection.execute("CREATE TABLE `test_legacy` (`id` INT AUTO_INCREMENT, `rank` ENUM('PLAYER','Administrator'), "
                + "`time` TIMESTAMP, `messages` LONGBLOB, `anything` LONGBLOB, PRIMARY KEY (`id`))").join();
        connection.execute("INSERT INTO `test_legacy` (`rank`, `time`, `messages`, `anything`) VALUES (?, ?, ?, ?)",
                "Administrator", new Timestamp(50_000), javaSerialized(new ArrayList<>(List.of("msg1", "msg2"))), null).join();

        var old = handler.getAllObjects(Legacy.class).join().getFirst();
        assertEquals(Rank.ADMIN, old.rank);
        assertEquals(new Timestamp(50_000), old.time);
        assertEquals(List.of("msg1", "msg2"), old.messages);

        // Written back as JSON into the old LONGBLOB column
        old.messages.add("msg3");
        handler.updateObject(old).join();
        assertEquals(List.of("msg1", "msg2", "msg3"), handler.getObjectWithID(Legacy.class, old.id).join().messages);
    }

    @Test
    void oldValuesOfOtherClassesAreNotDeserialized() throws IOException {
        connection.execute("CREATE TABLE `test_legacy` (`id` INT AUTO_INCREMENT, `rank` VARCHAR(64), "
                + "`time` TIMESTAMP, `messages` LONGBLOB, `anything` LONGBLOB, PRIMARY KEY (`id`))").join();
        connection.execute("INSERT INTO `test_legacy` (`anything`) VALUES (?)",
                (Object) javaSerialized(new ArrayList<>(List.of(new NotAllowed())))).join();

        var failure = assertThrows(CompletionException.class, () -> handler.getAllObjects(Legacy.class).join());
        assertTrue(failure.getCause().getMessage().contains("Refusing"), failure.getCause().getMessage());
    }

    @Test
    void failsInsteadOfHangingWithoutDatabase() {
        var unconnected = new DatabaseConnection(new DatabaseCredentials());
        var future = unconnected.getObjectHandler().getAllObjects(Everything.class);
        assertThrows(Exception.class, () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void createTableUsesQuotedNamesAndPlainColumnTypes() {
        var sql = new TableMapping<>(Everything.class).createTable();
        assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS `test_everything` (`id` INT NOT NULL AUTO_INCREMENT"), sql);
        assertTrue(sql.contains("`rank` VARCHAR(64)"), sql);
        assertTrue(sql.contains("`messages` LONGTEXT"), sql);
        assertTrue(sql.contains("`big_text` TEXT"), sql);
        assertTrue(sql.endsWith("PRIMARY KEY (`id`))"), sql);
    }

    private static byte[] javaSerialized(Serializable value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

}
