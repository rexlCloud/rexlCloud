package de.rexlcloud.wrapper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SystemMemoryTest {

    @Test
    void cachedMemoryCountsAsAvailable() {
        // The test server: 1.6 GB free, but 24 GB page cache
        var memory = SystemMemory.fromMeminfo(List.of(
                "MemTotal:       46241964 kB",
                "MemFree:         1667252 kB",
                "MemAvailable:   29205768 kB",
                "Cached:         24485228 kB"));

        assertEquals(46241964L * 1024, memory.total());
        assertEquals(29205768L * 1024, memory.available());
        assertEquals((46241964L - 29205768L) * 1024, memory.used());
    }

    @Test
    void kernelsWithoutMemAvailableAreNotGuessed() {
        assertNull(SystemMemory.fromMeminfo(List.of("MemTotal: 100 kB", "MemFree: 50 kB")));
    }

    @Test
    void readsThisMachine() {
        var memory = SystemMemory.read();
        assertTrue(memory.total() > 0);
        assertTrue(memory.available() > 0 && memory.available() <= memory.total());
    }

}
