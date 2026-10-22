package de.rexlcloud.wrapper.server;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrashBackoffTest {

    @Test
    void quickCrashesWaitLongerEachTimeUpToFiveMinutes() {
        var delays = IntStream.range(0, 8).mapToLong(i -> CrashBackoff.delayMillis("Quick", 2_000)).boxed().toList();
        assertEquals(List.of(5_000L, 10_000L, 20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L), delays);
    }

    @Test
    void aServerThatRanLongEnoughResetsItsGroup() {
        CrashBackoff.delayMillis("Reset", 1_000);
        CrashBackoff.delayMillis("Reset", 1_000);
        assertEquals(0, CrashBackoff.delayMillis("Reset", CrashBackoff.HEALTHY_RUN_MILLIS));
        assertEquals(5_000, CrashBackoff.delayMillis("Reset", 1_000));
    }

}
