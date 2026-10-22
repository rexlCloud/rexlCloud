package de.rexlcloud.wrapper.server.process;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The running Java process of a server, either a child of the wrapper or one in its own tmux session that keeps
 * running when the wrapper restarts.
 */
public interface ServerProcess {

    boolean isAlive();

    /**
     * Types a command into the server's console.
     */
    void send(String command) throws IOException;

    /**
     * Asks the process to end (SIGTERM); servers save their worlds and stop.
     */
    void terminate();

    /**
     * Ends the process immediately.
     */
    void kill();

    /**
     * Completes with the exit code once the process ended; -1 if it is unknown.
     */
    CompletableFuture<Integer> exit();

    /**
     * Passes the console output on line by line until the process ended and all output was read. Blocks; call it on
     * a thread of its own.
     */
    void readOutput(Consumer<String> line) throws IOException;

}
