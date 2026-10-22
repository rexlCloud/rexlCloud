package de.rexlcloud.core.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SafeObjectInputTest {

    record Coins(UUID player, int amount, List<String> history) implements Serializable {
    }

    private static byte[] serialize(Object object) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static final ClassLoader PLUGIN = SafeObjectInputTest.class.getClassLoader();

    @Test
    void jdkValueTypesAndThePluginsOwnClassesAreRead() throws Exception {
        var map = new HashMap<String, Object>();
        map.put("since", Instant.ofEpochSecond(5));
        map.put("ranks", new ArrayList<>(List.of("vip")));
        var coins = new Coins(UUID.randomUUID(), 42, List.of("+42"));

        assertEquals(map, SafeObjectInput.read(serialize(map), PLUGIN));
        assertEquals(coins, SafeObjectInput.read(serialize(coins), PLUGIN));
        assertArrayEquals(new int[]{1, 2}, (int[]) SafeObjectInput.read(serialize(new int[]{1, 2}), PLUGIN));
    }

    @Test
    void classesOfOtherPluginsAreRefused() throws Exception {
        var otherPlugin = new URLClassLoader(new URL[0], null);
        var bytes = serialize(new Coins(UUID.randomUUID(), 1, List.of()));

        assertThrows(IOException.class, () -> SafeObjectInput.read(bytes, otherPlugin));
    }

    @Test
    void serializedLambdasAreRefused() throws Exception {
        Runnable lambda = (Runnable & Serializable) () -> System.out.println("code from the network");
        var bytes = serialize(lambda);

        assertThrows(IOException.class, () -> SafeObjectInput.read(bytes, PLUGIN));
    }

}
