package de.rexlcloud.wrapper;

import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Memory of this machine as servers can use it. On Linux "free" memory leaves out the page cache, which the kernel
 * hands back as soon as programs need it; MemAvailable counts it, so a machine with a large cache is not reported
 * as nearly full.
 *
 * @param total     bytes in total
 * @param available bytes that new servers can use
 */
public record SystemMemory(long total, long available) {

    private static final Path MEMINFO = Path.of("/proc/meminfo");

    public long used() {
        return total - available;
    }

    public static SystemMemory read() {
        var os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        var total = os.getTotalMemorySize();
        if (Files.isReadable(MEMINFO)) {
            try {
                var fromProc = fromMeminfo(Files.readAllLines(MEMINFO));
                if (fromProc != null) {
                    return fromProc;
                }
            } catch (IOException ignored) {
                // fall back below
            }
        }
        return new SystemMemory(total, os.getFreeMemorySize());
    }

    /**
     * @return the memory from /proc/meminfo lines, or {@code null} if MemTotal or MemAvailable is missing
     */
    static SystemMemory fromMeminfo(List<String> lines) {
        long total = -1;
        long available = -1;
        for (var line : lines) {
            if (line.startsWith("MemTotal:")) {
                total = kilobytes(line);
            } else if (line.startsWith("MemAvailable:")) {
                available = kilobytes(line);
            }
        }
        return total > 0 && available >= 0 ? new SystemMemory(total, available) : null;
    }

    private static long kilobytes(String line) {
        var parts = line.trim().split("\\s+");
        return Long.parseLong(parts[1]) * 1024;
    }

}
