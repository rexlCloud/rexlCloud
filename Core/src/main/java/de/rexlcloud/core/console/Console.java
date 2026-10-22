package de.rexlcloud.core.console;

import de.rexlcloud.core.command.CommandRegistry;
import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.ConsoleAnimation;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Getter
public class Console implements Runnable {
    /**
     * Thread-safe, unlike SimpleDateFormat: lines are logged from many threads.
     */
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    /**
     * Sortable and valid on every file system (no colons).
     */
    private static final DateTimeFormatter ARCHIVE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Path LOGS = Path.of("logs");

    private final static String PROMPT = "cloud >> ";
    private static Console console;
    private final CommandRegistry commandRegistry;
    private final CommandCompleter commandCompleter;
    private final Terminal terminal;
    private final LineReader reader;
    /**
     * {@code false} once "stop" was entered. Set from the start, so code that runs before the console thread
     * does not see a stopped cloud.
     */
    private volatile boolean isRunning = true;
    private boolean debug = false;
    private FileWriter fileWriter;
    private final LogList logList;
    private final Queue<ConsoleAnimation> consoleAnimations;
    /**
     * Completions of the question {@link #ask} is waiting for, {@code null} otherwise.
     */
    private volatile List<AnimationCompletion> askCompletions;

    /**
     * Thrown by {@link #ask} when the user cancels with Ctrl+C.
     */
    public static class AskCancelledException extends RuntimeException {
        public AskCancelledException() {
            super("cancelled");
        }
    }

    public Console(CommandRegistry commandRegistry) throws IOException {
        logList = new LogList(100);
        consoleAnimations = new LinkedList<>();
        console = this;
        commandCompleter = new CommandCompleter(commandRegistry, console);
        System.setProperty("org.jline.terminal.dumb", "true");
        this.commandRegistry = commandRegistry;
        terminal = TerminalBuilder.terminal();
        var parser = new DefaultParser();
        reader = LineReaderBuilder.builder().terminal(terminal)
                .parser(parser).completer(commandCompleter)
                .variable("suggestions-min-buffer-size", 0).build();
        Files.createDirectories(LOGS);
        // Left over after a crash, or by older versions
        for (var leftover : new String[]{"latest.log", "latest-0.log"}) {
            var log = LOGS.resolve(leftover);
            if (Files.isRegularFile(log)) {
                archiveLog(LOGS, log, LocalDateTime.ofInstant(Files.getLastModifiedTime(log).toInstant(),
                        ZoneId.systemDefault()));
            }
        }
        fileWriter = new FileWriter(LOGS.resolve("latest.log").toFile(), StandardCharsets.UTF_8);
        var utf8 = StandardCharsets.UTF_8.name();
        final boolean[] print = { true };
        new Thread(() -> {
            var byteOutNormal = new ByteArrayOutputStream() {
                @Override
                public void flush() throws IOException {
                    if (print[0]) {
                        Console.write(toString(utf8));
                    }
                    print[0] = !print[0];
                    reset();
                }
            };
            try {
                var psNormal = new PrintStream(byteOutNormal, true, utf8);
                System.setOut(psNormal);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            var byteOutError = new ByteArrayOutputStream() {
                @Override
                public void flush() throws IOException {
                    var message = toString(utf8);
                    if (print[0]) {
                        Console.error(message);
                    }
                    print[0] = !message.contains("\n") || !print[0];
                    reset();
                }
            };
            try {
                var psError = new PrintStream(byteOutError, true, utf8);
                System.setErr(psError);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    public void setDebug() {
        this.debug = true;
    }

    /**
     * Starts asking the animation's questions in the console, or queues it if another one is running. Use this from
     * commands and modules for questions with several steps, like {@code group create}.
     */
    public static void addConsoleAnimation(ConsoleAnimation animation) {
        animation.onFinish(e -> {
            console.consoleAnimations.remove(e);
            if (console.consoleAnimations.isEmpty()) {
                console.forceClearConsole();
                console.logList.forEach(log -> {
                    console.reader.printAbove(log);
                });
            }
        });
        animation.onCancel(e -> {
            console.consoleAnimations.remove(e);
            if (console.consoleAnimations.isEmpty()) {
                console.forceClearConsole();
                console.logList.forEach(log -> {
                    console.reader.printAbove(log);
                });
            }
        });
        console.consoleAnimations.add(animation);
        if (console.consoleAnimations.size() == 1) {
            console.forceClearConsole();
            console.reader.printAbove(animation.nextQuestion().getQuestion());
        }
    }

    /**
     * Internal: asks a question and waits for the answer, for questions before the console reads commands (the
     * first-start setup). Modules use {@link #addConsoleAnimation(ConsoleAnimation)}: once the console runs, its own
     * thread reads the input. The answer is not logged.
     *
     * @param defaultAnswer returned for an empty answer, {@code null} for none; shown unless {@code hidden}
     * @param hidden        whether the input is masked, e.g. for passwords
     * @param completions   offered with Tab
     * @return the answer, the default for an empty one, or {@code null} if there is no console input (e.g. stdin
     *         is closed); then the caller should use its defaults
     * @throws AskCancelledException when the user presses Ctrl+C
     */
    public static String ask(String question, @Nullable String defaultAnswer, boolean hidden,
                             AnimationCompletion... completions) {
        // A hidden answer's default (e.g. a password) is never shown
        var shownDefault = hidden || defaultAnswer == null || defaultAnswer.isEmpty() ? "" : " [" + defaultAnswer + "]";
        var prompt = Color.INFO + question + shownDefault + Color.RESET + ": ";
        console.askCompletions = List.of(completions);
        try {
            var line = hidden ? console.reader.readLine(prompt, '*') : console.reader.readLine(prompt);
            line = line.trim();
            return line.isEmpty() && defaultAnswer != null ? defaultAnswer : line;
        } catch (UserInterruptException e) {
            throw new AskCancelledException();
        } catch (EndOfFileException | IOError e) {
            return null;
        } finally {
            console.askCompletions = null;
        }
    }

    public void stopLogging() {
        try {
            fileWriter.close();
            fileWriter = null;
            var log = LOGS.resolve("latest.log");
            if (Files.isRegularFile(log)) {
                archiveLog(LOGS, log, LocalDateTime.now());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            String line;
            try {
                line = reader.readLine(PROMPT).trim();
            } catch (UserInterruptException exception) {
                // Ctrl+C cancels a running question, otherwise only the current input
                var animation = consoleAnimations.poll();
                if (animation != null) {
                    animation.cancel();
                } else {
                    reader.printAbove("Use 'stop' to shut down");
                }
                continue;
            } catch (IOError | RuntimeException exception) {
                // EndOfFileException when stdin was closed, other errors when the terminal is gone. Reading again
                // would fail the same way forever, so stop reading; the cloud keeps running and stops on a signal.
                warn("Console input closed (%s), the cloud keeps running without it",
                        exception.getClass().getSimpleName());
                return;
            }
            try {
                handleLine(line);
            } catch (RuntimeException exception) {
                error("The command failed: %s", exception.getMessage());
                debug(stackTraceOf(exception));
            }
        }
    }

    private void handleLine(String line) {
        if (consoleAnimations.isEmpty()) {
            if (line.equals("stop")) {
                isRunning = false;
            }
            commandRegistry.executeCommand(line);
            return;
        }
        var ani = consoleAnimations.peek();
        var question = ani.nextQuestion();
        if (question.canBeAnswered(line)) {
            question.finish(line);
            if (!ani.isFinish()) {
                forceClearConsole();
                console.reader.printAbove(ani.nextQuestion().getQuestion());
            }
        } else {
            forceClearConsole();
            console.reader.printAbove(question.getQuestion());
        }
    }

    private static String stackTraceOf(Throwable throwable) {
        var writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    /**
     * Replaces each {@code %s} with the next placeholder, wrapped in the given colours.
     */
    private static String fill(String message, Object before, Object after, Object... placeholder) {
        for (var object : placeholder) {
            if (!message.contains("%s")) {
                break;
            }
            message = message.replaceFirst("%s", Matcher.quoteReplacement(String.valueOf(before) + object + after));
        }
        return message;
    }

    public static void error(String message, Object... placeholder) {
        message = fill(message, Color.HIGHLIGHT, Color.ERROR, placeholder);
        error(message);
    }

    public static void info(String message, Object... placeholder) {
        message = fill(message, Color.HIGHLIGHT, Color.INFO, placeholder);
        info(message);
    }

    public static void error(String message) {
        write(Color.ERROR + message);
    }

    public static void debug(String message, Object... placeholder) {
        message = fill(message, Color.HIGHLIGHT, Color.DEBUG, placeholder);
        debug(message);
    }

    public static void debug(String message) {
        if (console != null && console.isDebug()) {
            write(Color.DEBUG + message);
        }
    }

    public static void warn(String message, Object... placeholder) {
        message = fill(message, Color.HIGHLIGHT, Color.WARN, placeholder);
        warn(message);
    }

    public static void warn(String message) {
        write(Color.WARN + message);
    }

    public static void info(String message) {
        write(Color.INFO + message);
    }

    public static void write(String message, Object... placeholder) {
        message = fill(message, "", "", placeholder);
        write(message);
    }

    public static void write(String message) {
        write(message, true);
    }

    /**
     * Shows an info line on the screen but keeps it out of the log files, e.g. a generated password. Without an
     * interactive console it goes to standard output.
     */
    public static void infoOnScreenOnly(String message, Object... placeholder) {
        write(Color.INFO + fill(message, Color.HIGHLIGHT, Color.INFO, placeholder), false);
    }

    private static void write(String message, boolean log) {
        if (console == null) {
            // No interactive console yet (e.g. in tests)
            System.out.println(AttributedString.stripAnsi(message));
            return;
        }
        if (console.consoleAnimations.isEmpty()) {
            console.reader.printAbove(message + Color.RESET);
        } else {
            // Shown once the question on screen is answered
            console.logList.add(message);
        }
        if (log && console.fileWriter != null) {
            try {
                console.fileWriter.write(getDate() + " " + AttributedString.stripAnsi(message) + "\n");
                console.fileWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void clear() {
        clear(false);
    }

    private static void clear(boolean clearLog) {
        if (console.consoleAnimations.isEmpty()) {
            if (console.reader.isReading()) {
                console.reader.callWidget(LineReader.CLEAR_SCREEN);
            } else {
                console.terminal.puts(InfoCmp.Capability.clear_screen);
                console.terminal.flush();
            }
        }
        if (clearLog) {
            console.logList.clear();
        }
    }

    private void forceClearConsole() {
        if (console.reader.isReading()) {
            console.reader.callWidget(LineReader.CLEAR_SCREEN);
        } else {
            console.terminal.puts(InfoCmp.Capability.clear_screen);
            console.terminal.flush();
        }
    }

    /**
     * Zips a log as {@code <time>.zip} (with -1, -2 ... if that exists) and deletes it.
     *
     * @return the archive
     */
    static Path archiveLog(Path folder, Path log, LocalDateTime time) throws IOException {
        var name = ARCHIVE_NAME.format(time);
        var archive = folder.resolve(name + ".zip");
        for (int i = 1; Files.exists(archive); i++) {
            archive = folder.resolve(name + "-" + i + ".zip");
        }
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry(archive.getFileName().toString().replace(".zip", ".log")));
            Files.copy(log, zip);
            zip.closeEntry();
        }
        Files.delete(log);
        return archive;
    }

    private static String getDate() {
        return "[" + LOG_TIME.format(LocalDateTime.now()) + "]";
    }

}
