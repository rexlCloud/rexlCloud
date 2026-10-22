package de.rexlcloud.core.server.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the Java installations of this machine: the usual install folders of Linux, Windows and macOS, SDKMAN,
 * IntelliJ's ~/.jdks, the home folder, JAVA_HOME, PATH and extra folders from the configuration.
 */
public final class JavaRuntimeScanner {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    private static final String JAVA = WINDOWS ? "java.exe" : "java";
    private static final Pattern VERSION_OUTPUT = Pattern.compile("version \"([^\"]+)\"");

    private final List<Path> extraFolders;

    /**
     * @param extraFolders Java homes, or folders containing Java homes, to search besides the usual places
     */
    public JavaRuntimeScanner(Collection<Path> extraFolders) {
        this.extraFolders = List.copyOf(extraFolders);
    }

    /**
     * @return the runtimes found, newest major version first, each executable once
     */
    public List<JavaRuntime> scan() {
        var homes = new LinkedHashSet<Path>();
        candidateHomes().forEach(homes::add);

        Map<Path, JavaRuntime> runtimes = new LinkedHashMap<>();
        for (var home : homes) {
            executableOf(home).ifPresent(executable -> {
                var realExecutable = realPath(executable);
                if (!runtimes.containsKey(realExecutable)) {
                    describe(realExecutable).ifPresent(runtime -> runtimes.put(realExecutable, runtime));
                }
            });
        }
        var result = new ArrayList<>(runtimes.values());
        result.sort(Comparator.comparingInt(JavaRuntime::major).reversed()
                .thenComparing(JavaRuntime::version, Comparator.reverseOrder())
                .thenComparing(JavaRuntime::executable));
        return result;
    }

    private Stream<Path> candidateHomes() {
        var homes = new ArrayList<Path>();
        homes.add(Path.of(System.getProperty("java.home")));
        Optional.ofNullable(System.getenv("JAVA_HOME")).filter(home -> !home.isBlank()).map(Path::of).ifPresent(homes::add);
        javaOnPath().ifPresent(homes::add);

        var userHome = Path.of(System.getProperty("user.home"));
        var parents = new ArrayList<Path>(List.of(userHome, userHome.resolve(".jdks"),
                userHome.resolve(".sdkman/candidates/java")));
        if (WINDOWS) {
            for (var programFiles : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)")}) {
                if (programFiles == null) {
                    continue;
                }
                for (var vendor : new String[]{"Java", "Eclipse Adoptium", "Eclipse Foundation", "AdoptOpenJDK",
                        "Microsoft", "Zulu", "Amazon Corretto", "BellSoft", "OpenJDK", "Semeru"}) {
                    parents.add(Path.of(programFiles, vendor));
                }
            }
        } else {
            parents.addAll(List.of(Path.of("/usr/lib/jvm"), Path.of("/usr/java"), Path.of("/usr/local/java"),
                    Path.of("/opt/java"), Path.of("/opt"), Path.of("/usr/local"),
                    Path.of("/Library/Java/JavaVirtualMachines")));
        }
        for (var folder : extraFolders) {
            homes.add(folder);
            parents.add(folder);
        }
        for (var parent : parents) {
            homes.addAll(children(parent));
        }
        return homes.stream();
    }

    private static List<Path> children(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (var children = Files.list(folder)) {
            return children.filter(Files::isDirectory).sorted().toList();
        } catch (IOException | SecurityException e) {
            return List.of();
        }
    }

    /**
     * The home of the java found first on PATH.
     */
    private static Optional<Path> javaOnPath() {
        var path = System.getenv("PATH");
        if (path == null) {
            return Optional.empty();
        }
        for (var folder : path.split(java.io.File.pathSeparator)) {
            if (folder.isBlank()) {
                continue;
            }
            var java = Path.of(folder, JAVA);
            if (Files.isExecutable(java)) {
                var real = realPath(java);
                return Optional.ofNullable(real.getParent()).map(Path::getParent);
            }
        }
        return Optional.empty();
    }

    /**
     * bin/java of a Java home, also for macOS bundles (Contents/Home).
     */
    private static Optional<Path> executableOf(Path home) {
        for (var candidate : List.of(home.resolve("bin").resolve(JAVA), home.resolve("Contents/Home/bin").resolve(JAVA))) {
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    /**
     * Version and vendor from the {@code release} file of the Java home, or from {@code java -version}.
     */
    static Optional<JavaRuntime> describe(Path executable) {
        var home = executable.getParent() == null ? null : executable.getParent().getParent();
        var release = home == null ? null : home.resolve("release");
        if (release != null && Files.isRegularFile(release)) {
            try (var reader = Files.newBufferedReader(release, StandardCharsets.UTF_8)) {
                var properties = new Properties();
                properties.load(reader);
                var version = unquote(properties.getProperty("JAVA_VERSION"));
                if (!version.isBlank() && JavaRuntime.majorOf(version) > 0) {
                    return Optional.of(new JavaRuntime(executable.toString(), version, JavaRuntime.majorOf(version),
                            unquote(properties.getProperty("IMPLEMENTOR"))));
                }
            } catch (IOException | IllegalArgumentException e) {
                // fall back to running it
            }
        }
        return versionFromProcess(executable)
                .map(version -> new JavaRuntime(executable.toString(), version, JavaRuntime.majorOf(version), ""));
    }

    private static Optional<String> versionFromProcess(Path executable) {
        try {
            var process = new ProcessBuilder(executable.toString(), "-version").redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            var matcher = VERSION_OUTPUT.matcher(output);
            return matcher.find() && JavaRuntime.majorOf(matcher.group(1)) > 0 ? Optional.of(matcher.group(1)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        var trimmed = value.trim();
        return trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")
                ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
    }

}
