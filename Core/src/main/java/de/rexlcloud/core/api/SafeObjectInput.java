package de.rexlcloud.core.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Set;

/**
 * Reads objects that plugins send each other (message channels, player data) with Java serialization, but only of
 * harmless JDK value types and of the plugin's own classes. Deserializing arbitrary classes can run code, so
 * anything else, e.g. a crafted message from another server, is refused.
 */
final class SafeObjectInput {

    private static final Set<String> JDK_PACKAGES = Set.of("java.lang.", "java.util.", "java.time.", "java.math.");
    /**
     * JDK classes that are serializable but have no place in plugin data; serialized lambdas and reflection are
     * building blocks of deserialization attacks.
     */
    private static final Set<String> REFUSED_PREFIXES = Set.of("java.lang.invoke.", "java.lang.reflect.",
            "java.lang.ProcessBuilder", "java.lang.Runtime", "java.util.ServiceLoader");

    private SafeObjectInput() {
    }

    /**
     * @param loader classes of this loader (the plugin's) are allowed besides JDK value types
     * @throws IOException if the data is invalid or contains a class that is not allowed
     */
    static Object read(byte[] bytes, ClassLoader loader) throws IOException, ClassNotFoundException {
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass description) throws IOException, ClassNotFoundException {
                try {
                    return Class.forName(description.getName(), false, loader);
                } catch (ClassNotFoundException e) {
                    return super.resolveClass(description);
                }
            }
        }) {
            in.setObjectInputFilter(info -> filter(info, loader));
            return in.readObject();
        } catch (InvalidClassException e) {
            throw new IOException("Refusing to read " + e.classname + ": only JDK value types and the plugin's own "
                    + "classes are allowed", e);
        }
    }

    private static ObjectInputFilter.Status filter(ObjectInputFilter.FilterInfo info, ClassLoader loader) {
        if (info.depth() > 32 || info.references() > 100_000 || info.arrayLength() > 10_000_000) {
            return ObjectInputFilter.Status.REJECTED;
        }
        var type = info.serialClass();
        if (type == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }
        while (type.isArray()) {
            type = type.getComponentType();
        }
        if (type.isPrimitive()) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        var name = type.getName();
        if (REFUSED_PREFIXES.stream().anyMatch(name::startsWith)) {
            return ObjectInputFilter.Status.REJECTED;
        }
        if (JDK_PACKAGES.stream().anyMatch(name::startsWith) && type.getClassLoader() == null) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        if (loader != null && type.getClassLoader() == loader) {
            return ObjectInputFilter.Status.ALLOWED;
        }
        return ObjectInputFilter.Status.REJECTED;
    }

}
