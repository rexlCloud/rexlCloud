package de.rexlcloud.wrapper.server.process;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A server in its own tmux session, so it keeps running when the wrapper restarts; the restarted wrapper attaches
 * to it again. Its output goes to a file the wrapper follows, commands are typed in with {@code send-keys}, and the
 * Java process is signalled directly to stop it. Needs tmux, so Linux only.
 */
public final class TmuxServerProcess implements ServerProcess {

    /**
     * Files in the server folder; the cloud's temporary prefix keeps them out of saved templates.
     */
    static final String SCRIPT = ".rexlcloud-run.sh";
    static final String CONSOLE = ".rexlcloud-console.log";
    static final String EXIT = ".rexlcloud-exit";
    /**
     * The console file is emptied when it grows beyond this and everything was read.
     */
    private static final long MAX_CONSOLE_BYTES = 32L * 1024 * 1024;
    /**
     * When attaching to a running server, its console is read from this many bytes before the end.
     */
    private static final long ATTACH_TAIL_BYTES = 64 * 1024;

    private final String socket;
    private final String session;
    private final Path folder;
    private final ProcessHandle java;
    private final long readFrom;

    private TmuxServerProcess(String socket, String session, Path folder, ProcessHandle java, long readFrom) {
        this.socket = socket;
        this.session = session;
        this.folder = folder;
        this.java = java;
        this.readFrom = readFrom;
    }

    /**
     * Whether tmux can be used on this machine.
     */
    public static boolean available() {
        try {
            var process = new ProcessBuilder("tmux", "-V").redirectErrorStream(true).start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Starts {@code command} in {@code folder} in a new tmux session.
     */
    public static TmuxServerProcess start(String socket, String session, Path folder, List<String> command)
            throws IOException {
        // tmux does not run in the wrapper's folder: relative paths would point elsewhere
        folder = folder.toAbsolutePath();
        Files.deleteIfExists(folder.resolve(EXIT));
        Files.deleteIfExists(folder.resolve(CONSOLE));
        var script = folder.resolve(SCRIPT);
        Files.writeString(script, script(command), StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // not on Linux; tmux is not available there anyway
        }
        tmux(socket, "new-session", "-d", "-s", session, "-x", "220", "-y", "50", "-c", folder.toString(),
                "bash", script.toString());
        return new TmuxServerProcess(socket, session, folder, findJava(socket, session), 0);
    }

    /**
     * The server of a session that is still running, e.g. after the wrapper restarted.
     */
    public static Optional<TmuxServerProcess> attach(String socket, String session, Path folder) {
        folder = folder.toAbsolutePath();
        try {
            if (!hasSession(socket, session)) {
                return Optional.empty();
            }
            var java = findJava(socket, session);
            var console = folder.resolve(CONSOLE);
            var size = Files.exists(console) ? Files.size(console) : 0;
            return Optional.of(new TmuxServerProcess(socket, session, folder, java, Math.max(0, size - ATTACH_TAIL_BYTES)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static boolean hasSession(String socket, String session) {
        try {
            tmux(socket, "has-session", "-t", "=" + session);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * The start script: the server's output goes to the pane and the console file, its exit code into a file.
     */
    static String script(List<String> command) {
        var quoted = new ArrayList<String>();
        for (var part : command) {
            quoted.add("'" + part.replace("'", "'\\''") + "'");
        }
        return "#!/bin/bash\n"
                + "cd \"$(dirname \"$0\")\"\n"
                + String.join(" ", quoted) + " 2>&1 | tee -a " + CONSOLE + "\n"
                + "echo \"${PIPESTATUS[0]}\" > " + EXIT + "\n";
    }

    private static ProcessHandle findJava(String socket, String session) throws IOException {
        long panePid;
        try {
            panePid = Long.parseLong(tmux(socket, "display-message", "-p", "-t", pane(session), "#{pane_pid}").strip());
        } catch (NumberFormatException e) {
            throw new IOException("tmux did not report the process of the session " + session);
        }
        // The pane runs bash, which starts Java and tee
        for (var attempt = 0; attempt < 100; attempt++) {
            var shell = ProcessHandle.of(panePid);
            if (shell.isEmpty()) {
                break;
            }
            var java = shell.get().descendants()
                    .filter(handle -> handle.info().command().map(command -> command.endsWith("java")).orElse(false))
                    .findFirst();
            if (java.isPresent()) {
                return java.get();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IOException("The Java process of the tmux session " + session + " was not found");
    }

    /**
     * The pane of exactly this session; "=" alone only matches sessions, not panes.
     */
    private static String pane(String session) {
        return "=" + session + ":";
    }

    private static String tmux(String socket, String... arguments) throws IOException {
        var command = new ArrayList<>(List.of("tmux", "-L", socket));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("tmux did not answer: " + String.join(" ", arguments));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException("tmux " + arguments[0] + " failed: " + output.strip());
        }
        return output;
    }

    public String getSession() {
        return session;
    }

    @Override
    public boolean isAlive() {
        return java.isAlive();
    }

    @Override
    public void send(String command) throws IOException {
        tmux(socket, "send-keys", "-t", pane(session), "-l", "--", command);
        tmux(socket, "send-keys", "-t", pane(session), "Enter");
    }

    @Override
    public void terminate() {
        java.destroy();
    }

    @Override
    public void kill() {
        java.destroyForcibly();
        try {
            tmux(socket, "kill-session", "-t", "=" + session);
        } catch (IOException ignored) {
            // already gone
        }
    }

    @Override
    public CompletableFuture<Integer> exit() {
        // Not a child of this process: the exit code comes from the file the start script writes
        return java.onExit().thenApplyAsync(ended -> {
            var file = folder.resolve(EXIT);
            for (var attempt = 0; attempt < 50; attempt++) {
                try {
                    if (Files.exists(file)) {
                        return Integer.parseInt(Files.readString(file).strip());
                    }
                    Thread.sleep(100);
                } catch (IOException | NumberFormatException e) {
                    return -1;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            }
            return -1;
        });
    }

    @Override
    public void readOutput(Consumer<String> line) throws IOException {
        var console = folder.resolve(CONSOLE);
        var position = readFrom;
        var pending = new StringBuilder();
        var buffer = new byte[64 * 1024];
        while (true) {
            var read = 0;
            if (Files.exists(console)) {
                try (var file = new RandomAccessFile(console.toFile(), "r")) {
                    if (file.length() < position) {
                        // Emptied (see below)
                        position = 0;
                    }
                    file.seek(position);
                    read = file.read(buffer);
                    if (read > 0) {
                        position += read;
                        pending.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                        int newline;
                        while ((newline = pending.indexOf("\n")) >= 0) {
                            line.accept(pending.substring(0, newline).replace("\r", ""));
                            pending.delete(0, newline + 1);
                        }
                    } else if (position > MAX_CONSOLE_BYTES && pending.isEmpty()) {
                        // tee appends, so the file can be emptied while it writes
                        try (var truncate = new RandomAccessFile(console.toFile(), "rw")) {
                            truncate.setLength(0);
                        }
                        position = 0;
                    }
                }
            }
            if (read <= 0) {
                if (!java.isAlive() && !hasSession(socket, session)) {
                    if (!pending.isEmpty()) {
                        line.accept(pending.toString());
                    }
                    return;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

}
