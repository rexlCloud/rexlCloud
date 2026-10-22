package de.rexlcloud.master.wrapper;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Picks the wrapper a server starts on, from those that are able to run it.
 */
final class WrapperChoice {

    /**
     * CPU load (0 to 1) from which a wrapper only gets new servers if no other has room.
     */
    static final double BUSY_CPU = 0.9;

    private WrapperChoice() {
    }

    /**
     * Among the wrappers with at least {@code needed} bytes free, the one with the most free memory; wrappers whose
     * CPU is busy only if no other fits.
     *
     * @param free bytes a new server can get on a wrapper
     * @param cpu  a wrapper's CPU load from 0 to 1, negative if unknown
     */
    static <W> Optional<W> choose(List<W> candidates, ToLongFunction<W> free, ToDoubleFunction<W> cpu, long needed) {
        var fitting = candidates.stream().filter(wrapper -> free.applyAsLong(wrapper) >= needed).toList();
        var notBusy = fitting.stream().filter(wrapper -> cpu.applyAsDouble(wrapper) < BUSY_CPU).toList();
        return (notBusy.isEmpty() ? fitting : notBusy).stream().max(Comparator.comparingLong(free));
    }

}
