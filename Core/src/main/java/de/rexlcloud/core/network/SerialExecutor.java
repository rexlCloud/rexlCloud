package de.rexlcloud.core.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the packets of one connection one after another, in the order they arrived, on a virtual thread. Handlers
 * may block (e.g. copy files) without blocking Netty's event loop or other connections, and a packet can never
 * overtake one that was sent before it.
 */
public final class SerialExecutor implements Executor {

    private static final ExecutorService VIRTUAL_THREADS = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("packet-handler-", 0).factory());

    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean();

    @Override
    public void execute(Runnable task) {
        tasks.add(task);
        schedule();
    }

    private void schedule() {
        if (running.compareAndSet(false, true)) {
            VIRTUAL_THREADS.execute(this::drain);
        }
    }

    private void drain() {
        try {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable e) {
                    // One failing packet must not stop the ones after it
                    e.printStackTrace();
                }
            }
        } finally {
            running.set(false);
            // A task added between the last poll and resetting the flag
            if (!tasks.isEmpty()) {
                schedule();
            }
        }
    }

}
