package de.rexlcloud.core.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WrapperReconnectTest {

    @Test
    void givesUpOnlyAfterTheWrapperStayedAway() {
        var attempts = new AtomicInteger();
        var gaveUp = new AtomicInteger();
        var log = new ArrayList<String>();
        var reconnect = new WrapperReconnect(attempts::incrementAndGet, gaveUp::incrementAndGet, log::add, 0);

        reconnect.lost();

        assertEquals(1, gaveUp.get(), "a give-up time of 0 stops right away");
        reconnect.lost();
        assertEquals(1, gaveUp.get(), "only once");
        assertEquals(0, attempts.get());
    }

    @Test
    void aConnectionResetsTheClock() {
        var gaveUp = new AtomicInteger();
        List<String> log = new ArrayList<>();
        var reconnect = new WrapperReconnect(() -> {
        }, gaveUp::incrementAndGet, log::add);

        reconnect.lost();
        reconnect.connected();

        assertEquals(0, gaveUp.get());
        assertTrue(log.getFirst().startsWith("Lost the connection"));
        assertEquals("Connected to the wrapper again", log.getLast());
    }

    @Test
    void aStoppingServerDoesNotReconnect() {
        var gaveUp = new AtomicInteger();
        var reconnect = new WrapperReconnect(() -> fail("no attempt"), gaveUp::incrementAndGet, message -> {
        }, 0);

        reconnect.stop();
        reconnect.lost();

        assertEquals(0, gaveUp.get());
    }

}
