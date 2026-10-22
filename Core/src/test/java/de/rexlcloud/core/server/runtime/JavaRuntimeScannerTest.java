package de.rexlcloud.core.server.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaRuntimeScannerTest {

    private static final String JAVA = System.getProperty("os.name", "").toLowerCase().startsWith("windows")
            ? "java.exe" : "java";

    @TempDir
    Path folder;

    /**
     * A Java home with a release file and a bin/java that is never run.
     */
    private Path jdk(String name, String release) throws IOException {
        var home = folder.resolve(name);
        Files.createDirectories(home.resolve("bin"));
        var java = Files.writeString(home.resolve("bin").resolve(JAVA), "");
        java.toFile().setExecutable(true);
        Files.writeString(home.resolve("release"), release);
        return java;
    }

    private List<JavaRuntime> scanned() {
        // The scanner also finds the Java installations of this machine; only look at the test ones
        var root = folder.toAbsolutePath().toString();
        return new JavaRuntimeScanner(List.of(folder)).scan().stream()
                .filter(runtime -> Path.of(runtime.executable()).toAbsolutePath().toString().startsWith(root)
                        || runtime.executable().startsWith(realRoot()))
                .toList();
    }

    private String realRoot() {
        try {
            return folder.toRealPath().toString();
        } catch (IOException e) {
            return folder.toString();
        }
    }

    @Test
    void findsJavaHomesInExtraFoldersNewestFirst() throws IOException {
        jdk("jdk-21", "IMPLEMENTOR=\"Eclipse Adoptium\"\nJAVA_VERSION=\"21.0.5\"\n");
        jdk("jre-8", "JAVA_VERSION=\"1.8.0_402\"\n");
        jdk("jdk-25", "IMPLEMENTOR=\"Oracle Corporation\"\nJAVA_VERSION=\"25.0.1\"\n");
        Files.createDirectories(folder.resolve("not-a-jdk"));

        var runtimes = scanned();

        assertEquals(List.of(25, 21, 8), runtimes.stream().map(JavaRuntime::major).toList());
        assertEquals("21.0.5", runtimes.get(1).version());
        assertEquals("Eclipse Adoptium", runtimes.get(1).vendor());
        assertEquals("Java 21.0.5 (Eclipse Adoptium)", runtimes.get(1).displayName());
        assertEquals("Java 1.8.0_402", runtimes.get(2).displayName());
        assertTrue(runtimes.get(0).executable().endsWith(Path.of("jdk-25", "bin", JAVA).toString()));
    }

    @Test
    void aFolderThatIsItselfAJavaHomeIsFound() throws IOException {
        var java = jdk("only", "JAVA_VERSION=\"17.0.12\"\n");

        var runtimes = new JavaRuntimeScanner(List.of(folder.resolve("only"))).scan();

        assertTrue(runtimes.stream().anyMatch(runtime -> runtime.major() == 17
                && Path.of(runtime.executable()).endsWith(Path.of("only", "bin", JAVA))), runtimes.toString());
        assertTrue(Files.exists(java));
    }

    @Test
    void theRunningJavaIsAlwaysFound() {
        var runtimes = new JavaRuntimeScanner(List.of()).scan();
        assertTrue(runtimes.stream().anyMatch(runtime -> runtime.major() == Runtime.version().feature()), runtimes.toString());
    }

    @Test
    void majorVersions() {
        assertEquals(8, JavaRuntime.majorOf("1.8.0_402"));
        assertEquals(25, JavaRuntime.majorOf("25.0.4"));
        assertEquals(21, JavaRuntime.majorOf("21"));
        assertEquals(26, JavaRuntime.majorOf("26-ea"));
        assertEquals(0, JavaRuntime.majorOf("abc"));
    }

}
