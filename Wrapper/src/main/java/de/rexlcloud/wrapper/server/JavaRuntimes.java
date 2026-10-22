package de.rexlcloud.wrapper.server;

import de.rexlcloud.core.server.runtime.JavaRuntime;
import de.rexlcloud.core.server.runtime.JavaRuntimeScanner;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * The Java installations of this machine, scanned again every few minutes so new installations show up.
 */
public class JavaRuntimes {

    private static final long RESCAN_MILLIS = 5 * 60_000;

    private final JavaRuntimeScanner scanner;
    private List<JavaRuntime> runtimes;
    private long scannedAt;

    public JavaRuntimes(Collection<String> extraFolders) {
        this.scanner = new JavaRuntimeScanner(extraFolders.stream().map(Path::of).toList());
    }

    public synchronized List<JavaRuntime> get() {
        if (runtimes == null || System.currentTimeMillis() - scannedAt > RESCAN_MILLIS) {
            runtimes = scanner.scan();
            scannedAt = System.currentTimeMillis();
        }
        return runtimes;
    }

}
