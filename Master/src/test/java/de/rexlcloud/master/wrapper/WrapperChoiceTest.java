package de.rexlcloud.master.wrapper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WrapperChoiceTest {

    record W(String name, long free, double cpu) {
    }

    private static Optional<String> choose(long needed, W... wrappers) {
        return WrapperChoice.choose(List.of(wrappers), W::free, W::cpu, needed).map(W::name);
    }

    @Test
    void theWrapperWithTheMostFreeMemoryWins() {
        assertEquals("b", choose(1024, new W("a", 2048, 0.1), new W("b", 4096, 0.5)).orElseThrow());
    }

    @Test
    void wrappersWithoutRoomAreNeverChosen() {
        assertTrue(choose(4096, new W("a", 2048, 0.1), new W("b", 4095, 0.1)).isEmpty());
        assertEquals("b", choose(4096, new W("a", 2048, 0.1), new W("b", 4096, 0.1)).orElseThrow());
    }

    @Test
    void aBusyCpuOnlyCountsIfThereIsAnAlternative() {
        assertEquals("a", choose(1024, new W("a", 2048, 0.2), new W("b", 8192, 0.95)).orElseThrow());
        assertEquals("b", choose(1024, new W("a", 512, 0.2), new W("b", 8192, 0.95)).orElseThrow(),
                "the busy wrapper is better than none");
        assertEquals("b", choose(1024, new W("a", 2048, -1), new W("b", 8192, -1)).orElseThrow(),
                "unknown CPU is not busy");
    }

}
