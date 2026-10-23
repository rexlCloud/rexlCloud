package de.rexlcloud.core.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDataValuesTest {

    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    /**
     * A plugin's own class: not a JDK value type.
     */
    record Stats(int kills) implements Serializable {
    }

    private static byte[] serialize(Serializable value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object read(byte[] bytes) throws Exception {
        return SafeObjectInput.read(bytes, null);
    }

    @Test
    void writtenValuesHaveTheTypePluginsReadThemWith() throws Exception {
        assertEquals(250, read(PlayerDataValues.encode("int", new JsonPrimitive(250))));
        assertEquals(250, read(PlayerDataValues.encode("int", new JsonPrimitive("250"))));
        assertEquals(5_000_000_000L, read(PlayerDataValues.encode("long", new JsonPrimitive(5_000_000_000L))));
        assertEquals((short) 3, read(PlayerDataValues.encode("short", new JsonPrimitive(3))));
        assertEquals((byte) 3, read(PlayerDataValues.encode("byte", new JsonPrimitive(3))));
        assertEquals(1.5, read(PlayerDataValues.encode("double", new JsonPrimitive(1.5))));
        assertEquals(1.5f, read(PlayerDataValues.encode("float", new JsonPrimitive("1.5"))));
        assertEquals(true, read(PlayerDataValues.encode("boolean", new JsonPrimitive("TRUE"))));
        assertEquals("VIP", read(PlayerDataValues.encode("string", new JsonPrimitive("VIP"))));
        assertEquals("250", read(PlayerDataValues.encode("string", new JsonPrimitive(250))));
        assertEquals(PLAYER, read(PlayerDataValues.encode("uuid", new JsonPrimitive(PLAYER.toString()))));
    }

    @Test
    void valuesThatDoNotFitTheirTypeAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> PlayerDataValues.encode("int", new JsonPrimitive(1.5)));
        assertThrows(IllegalArgumentException.class, () -> PlayerDataValues.encode("int", new JsonPrimitive(5_000_000_000L)));
        assertThrows(IllegalArgumentException.class, () -> PlayerDataValues.encode("byte", new JsonPrimitive(300)));
        assertThrows(IllegalArgumentException.class, () -> PlayerDataValues.encode("double", new JsonPrimitive("NaN")));
        assertThrows(IllegalArgumentException.class, () -> PlayerDataValues.encode("boolean", new JsonPrimitive("yes")));
        assertThrows(IllegalArgumentException.class, () -> PlayerDataValues.encode("uuid", new JsonPrimitive("nope")));
        assertThrows(IllegalArgumentException.class, () -> PlayerDataValues.encode("object", new JsonPrimitive(1)));
        assertThrows(IllegalArgumentException.class, () -> PlayerDataValues.encode("string", new JsonArray()));
        assertThrows(IllegalArgumentException.class, () -> PlayerDataValues.encode("string",
                new JsonPrimitive("x".repeat(PlayerDataValues.MAX_STRING_LENGTH + 1))));
    }

    @Test
    void typesAreGuessedFromJson() {
        assertEquals("int", PlayerDataValues.guessType(new JsonPrimitive(250)));
        assertEquals("int", PlayerDataValues.guessType(new JsonPrimitive(2.0)));
        assertEquals("long", PlayerDataValues.guessType(new JsonPrimitive(5_000_000_000L)));
        assertEquals("double", PlayerDataValues.guessType(new JsonPrimitive(1.5)));
        assertEquals("boolean", PlayerDataValues.guessType(new JsonPrimitive(false)));
        assertEquals("string", PlayerDataValues.guessType(new JsonPrimitive("250")));
    }

    @Test
    void storedValuesAreDescribed() throws Exception {
        var coins = PlayerDataValues.describe(new PlayerDatabaseData(PLAYER, "coins", serialize(250)));
        assertEquals("coins", coins.get("key").getAsString());
        assertEquals("int", coins.get("type").getAsString());
        assertEquals(250, coins.get("value").getAsInt());
        assertTrue(coins.get("readable").getAsBoolean());
        assertTrue(coins.get("editable").getAsBoolean());

        var map = new LinkedHashMap<String, Object>();
        map.put("kills", 3);
        map.put("maps", new ArrayList<>(List.of("Castle")));
        var stats = PlayerDataValues.describe(new PlayerDatabaseData(PLAYER, "stats", serialize(map)));
        assertEquals("map", stats.get("type").getAsString());
        assertEquals(3, stats.getAsJsonObject("value").get("kills").getAsInt());
        assertEquals("Castle", stats.getAsJsonObject("value").getAsJsonArray("maps").get(0).getAsString());
        assertFalse(stats.get("editable").getAsBoolean());

        var own = PlayerDataValues.describe(new PlayerDatabaseData(PLAYER, "own", serialize(new Stats(1))));
        assertFalse(own.get("readable").getAsBoolean());
        assertEquals(Stats.class.getName(), own.get("type").getAsString());
        assertTrue(own.get("value").isJsonNull());

        var broken = PlayerDataValues.describe(new PlayerDatabaseData(PLAYER, "broken", new byte[]{1, 2, 3}));
        assertFalse(broken.get("readable").getAsBoolean());
        assertEquals("unknown", broken.get("type").getAsString());
    }

    @Test
    void theWritableTypeOfAStoredValue() throws Exception {
        assertEquals("int", PlayerDataValues.writableType(serialize(250)));
        assertEquals("uuid", PlayerDataValues.writableType(serialize(PLAYER)));
        assertNull(PlayerDataValues.writableType(serialize(new ArrayList<>(List.of(1)))));
        assertNull(PlayerDataValues.writableType(serialize(new Stats(1))));
    }

}
