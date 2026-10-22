package de.rexlcloud.core.console;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class LogList extends ArrayList<String> implements Serializable {

    private final int capacity;
    private transient volatile List<Consumer<String>> listeners;

    public LogList(int initialCapacity) {
        super();
        this.capacity = initialCapacity;
    }

    public LogList() {
        super();
        this.capacity = -1;
    }

    @Override
    public synchronized void clear() {
        super.clear();
    }

    @Override
    public synchronized String[] toArray() {
        return super.toArray(String[]::new);
    }

    /**
     * Thread-safe copy of the current lines, oldest first.
     */
    public synchronized List<String> snapshot() {
        return new ArrayList<>(this);
    }

    @Override
    public boolean add(String line) {
        synchronized (this) {
            if (capacity != -1 && size() > capacity) {
                super.remove(0);
            }
            super.add(line);
        }
        var current = listeners;
        if (current != null) {
            for (var listener : current) {
                try {
                    listener.accept(line);
                } catch (RuntimeException e) {
                    // a failing listener must not break log collection
                    Console.debug("LogList listener failed: %s", e.getMessage());
                }
            }
        }
        return true;
    }

    /**
     * Registers a listener that is called for every line added after registration.
     *
     * @return a handle that removes the listener again when closed
     */
    public AutoCloseable addListener(Consumer<String> listener) {
        var current = listeners();
        current.add(listener);
        return () -> current.remove(listener);
    }

    private synchronized List<Consumer<String>> listeners() {
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<>();
        }
        return listeners;
    }

}
