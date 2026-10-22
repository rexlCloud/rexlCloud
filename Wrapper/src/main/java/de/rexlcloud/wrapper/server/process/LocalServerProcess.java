package de.rexlcloud.wrapper.server.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A server process that is a child of the wrapper: it ends with the wrapper.
 */
public final class LocalServerProcess implements ServerProcess {

    private final Process process;
    private final Writer console;

    private LocalServerProcess(Process process) {
        this.process = process;
        this.console = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
    }

    public static LocalServerProcess start(File folder, List<String> command) throws IOException {
        return new LocalServerProcess(new ProcessBuilder(command).redirectErrorStream(true).directory(folder).start());
    }

    @Override
    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public synchronized void send(String command) throws IOException {
        // The console stays open: older servers (e.g. Paper 1.8) stop when their input ends
        console.write(command + "\n");
        console.flush();
    }

    @Override
    public void terminate() {
        process.destroy();
    }

    @Override
    public void kill() {
        process.destroyForcibly();
    }

    @Override
    public CompletableFuture<Integer> exit() {
        return process.onExit().thenApply(Process::exitValue);
    }

    @Override
    public void readOutput(Consumer<String> line) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            for (var next = reader.readLine(); next != null; next = reader.readLine()) {
                line.accept(next);
            }
        }
    }

}
