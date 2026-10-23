package de.rexlcloud.core.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Internal: shows player data as JSON and turns JSON into player data, for the REST API and the web panel. Only JDK
 * value types are read (like {@link Player#getData(String)} does without a plugin); values of a plugin's own classes
 * are reported with their class name but not read.
 * <p>
 * Plugins read a value with a fixed type ({@code player.<Integer>getData("coins")}), so values are written with an
 * explicit type: one of {@link #WRITABLE_TYPES}.
 */
public final class PlayerDataValues {

    /**
     * Types {@link #encode(String, JsonElement)} writes, in the order the web panel offers them.
     */
    public static final List<String> WRITABLE_TYPES = List.of("string", "int", "long", "double", "float", "boolean",
            "short", "byte", "uuid");

    /**
     * Longest text {@link #encode} stores.
     */
    public static final int MAX_STRING_LENGTH = 65_536;

    private PlayerDataValues() {
    }

    /**
     * @return {@code key}, {@code type} (e.g. {@code int}, {@code list} or a class name), {@code value} (JSON, null
     *         if it cannot be read), {@code readable}, {@code editable} (a {@link #WRITABLE_TYPES} value) and
     *         {@code size} in bytes
     */
    public static JsonObject describe(PlayerDatabaseData data) {
        var json = new JsonObject();
        json.addProperty("key", data.getKey());
        var bytes = data.getValue();
        json.addProperty("size", bytes == null ? 0 : bytes.length);
        try {
            var value = SafeObjectInput.read(bytes, null);
            var type = typeName(value);
            json.addProperty("type", type);
            json.add("value", toJson(value));
            json.addProperty("readable", true);
            json.addProperty("editable", WRITABLE_TYPES.contains(type));
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            json.addProperty("type", className(bytes));
            json.add("value", JsonNull.INSTANCE);
            json.addProperty("readable", false);
            json.addProperty("editable", false);
        }
        return json;
    }

    /**
     * @return the {@link #WRITABLE_TYPES} type of a stored value, or {@code null} if it has another type or cannot
     *         be read
     */
    public static String writableType(byte[] bytes) {
        try {
            var type = typeName(SafeObjectInput.read(bytes, null));
            return WRITABLE_TYPES.contains(type) ? type : null;
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The type a JSON value is written as when no type is given: {@code boolean}, {@code int} or {@code long} for
     * whole numbers, {@code double} for other numbers, otherwise {@code string}.
     */
    public static String guessType(JsonElement value) {
        if (value != null && value.isJsonPrimitive()) {
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return "boolean";
            }
            if (primitive.isNumber()) {
                var number = primitive.getAsBigDecimal();
                if (number.stripTrailingZeros().scale() <= 0) {
                    try {
                        number.intValueExact();
                        return "int";
                    } catch (ArithmeticException e) {
                        return "long";
                    }
                }
                return "double";
            }
        }
        return "string";
    }

    /**
     * Serializes a JSON value as the given type, the way {@link Player#setData} stores it.
     *
     * @param type  one of {@link #WRITABLE_TYPES}
     * @param value a JSON string, number or boolean (numbers and booleans may also be given as text)
     * @throws IllegalArgumentException if the type is unknown or the value does not fit it
     */
    public static byte[] encode(String type, JsonElement value) {
        if (type == null || !WRITABLE_TYPES.contains(type.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unknown type " + type + ", use one of " + String.join(", ", WRITABLE_TYPES));
        }
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("The value must be a text, number or boolean");
        }
        var text = value.getAsString().trim();
        Serializable object;
        try {
            object = switch (type.toLowerCase(Locale.ROOT)) {
                case "string" -> {
                    if (value.getAsString().length() > MAX_STRING_LENGTH) {
                        throw new IllegalArgumentException("Texts can have at most " + MAX_STRING_LENGTH + " characters");
                    }
                    yield value.getAsString();
                }
                case "int" -> new BigDecimal(text).intValueExact();
                case "long" -> new BigDecimal(text).longValueExact();
                case "short" -> new BigDecimal(text).shortValueExact();
                case "byte" -> new BigDecimal(text).byteValueExact();
                case "double" -> finite(Double.parseDouble(text));
                case "float" -> {
                    var number = Float.parseFloat(text);
                    finite(number);
                    yield number;
                }
                case "boolean" -> switch (text.toLowerCase(Locale.ROOT)) {
                    case "true" -> true;
                    case "false" -> false;
                    default -> throw new IllegalArgumentException("A boolean is true or false, not " + text);
                };
                case "uuid" -> UUID.fromString(text);
                default -> throw new IllegalStateException(type);
            };
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException(text + " is not a valid " + type, e);
        }
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(bytes)) {
                out.writeObject(object);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static double finite(double number) {
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Only finite numbers can be stored");
        }
        return number;
    }

    private static String typeName(Object value) {
        return switch (value) {
            case null -> "null";
            case String ignored -> "string";
            case Integer ignored -> "int";
            case Long ignored -> "long";
            case Double ignored -> "double";
            case Float ignored -> "float";
            case Short ignored -> "short";
            case Byte ignored -> "byte";
            case Boolean ignored -> "boolean";
            case Character ignored -> "char";
            case UUID ignored -> "uuid";
            case List<?> ignored -> "list";
            case Set<?> ignored -> "set";
            case Map<?, ?> ignored -> "map";
            default -> value.getClass().getName();
        };
    }

    private static JsonElement toJson(Object value) {
        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case Boolean bool -> new JsonPrimitive(bool);
            case Double number when !Double.isFinite(number) -> new JsonPrimitive(number.toString());
            case Float number when !Float.isFinite(number) -> new JsonPrimitive(number.toString());
            case Number number -> new JsonPrimitive(number);
            case Collection<?> collection -> {
                var array = new JsonArray();
                collection.forEach(element -> array.add(toJson(element)));
                yield array;
            }
            case Map<?, ?> map -> {
                var object = new JsonObject();
                map.forEach((key, element) -> object.add(String.valueOf(key), toJson(element)));
                yield object;
            }
            case Object[] array -> {
                var json = new JsonArray();
                for (var element : array) {
                    json.add(toJson(element));
                }
                yield json;
            }
            default -> new JsonPrimitive(value.toString());
        };
    }

    /**
     * The class of a value that is not read: only its name is taken from the stream, nothing is loaded.
     */
    private static String className(byte[] bytes) {
        if (bytes == null) {
            return "unknown";
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass description) throws IOException {
                throw new ClassNameFound(description.getName());
            }

            @Override
            protected Class<?> resolveProxyClass(String[] interfaces) throws IOException {
                throw new ClassNameFound("proxy");
            }
        }) {
            in.readObject();
        } catch (ClassNameFound found) {
            return found.getMessage();
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            // not a serialized object
        }
        return "unknown";
    }

    private static final class ClassNameFound extends IOException {
        private ClassNameFound(String name) {
            super(name);
        }
    }

}
