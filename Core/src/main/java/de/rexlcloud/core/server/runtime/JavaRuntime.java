package de.rexlcloud.core.server.runtime;

import lombok.NonNull;

import java.io.Serial;
import java.io.Serializable;

/**
 * A Java installation found on a wrapper.
 *
 * @param executable absolute path of its {@code java} executable, used as the java command of a group
 * @param version    full version, e.g. {@code 25.0.4} or {@code 1.8.0_402}
 * @param major      feature version, e.g. 25 or 8
 * @param vendor     e.g. "Eclipse Adoptium", or {@code ""} if unknown
 */
public record JavaRuntime(@NonNull String executable, @NonNull String version, int major, @NonNull String vendor)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Major version of a Java version string: {@code 1.8.0_402} is 8, {@code 25.0.4} is 25.
     *
     * @return the major version, or 0 if the string is not a version
     */
    public static int majorOf(String version) {
        var parts = version.trim().split("[._+-]");
        try {
            var first = Integer.parseInt(parts[0]);
            return first == 1 && parts.length > 1 ? Integer.parseInt(parts[1]) : first;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * e.g. "Java 25.0.4 (Eclipse Adoptium)".
     */
    public String displayName() {
        return "Java " + version + (vendor.isBlank() ? "" : " (" + vendor + ")");
    }

}
