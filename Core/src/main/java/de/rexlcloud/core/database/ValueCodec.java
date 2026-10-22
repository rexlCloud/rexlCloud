package de.rexlcloud.core.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.rexlcloud.core.database.anotations.DatabaseValue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;

/**
 * Converts the value of one {@link DatabaseValue} field to a JDBC value and back, and picks its column type.
 * <p>
 * Simple types get their own column type. Everything else (lists, maps, records, other objects) is stored as JSON
 * using the generic type of the field, so {@code List<UUID>} comes back as a list of UUIDs.
 */
final class ValueCodec {

    static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(Instant.class, isoAdapter(Instant::parse))
            .registerTypeAdapter(LocalDateTime.class, isoAdapter(LocalDateTime::parse))
            .registerTypeAdapter(LocalDate.class, isoAdapter(LocalDate::parse))
            .registerTypeAdapter(Timestamp.class, new TimestampAdapter())
            .create();

    /**
     * Classes that rows written by rexlCloud 1.x/2.0 may contain. Java serialization of anything else is rejected,
     * because deserializing arbitrary classes can run code.
     */
    private static final ObjectInputFilter LEGACY_FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=32;maxrefs=1000000;maxarray=10000000;"
                    + "java.lang.*;java.util.*;java.time.*;java.math.*;java.sql.Timestamp;java.sql.Date;!*");
    private static final byte[] JAVA_SERIALIZATION_MAGIC = {(byte) 0xAC, (byte) 0xED};

    private enum Kind {
        STRING, INT, LONG, SHORT, BYTE, DOUBLE, FLOAT, BOOLEAN, BYTES, UUID, ENUM,
        TIMESTAMP, SQL_DATE, INSTANT, LOCAL_DATE_TIME, LOCAL_DATE, JSON
    }

    private final Class<?> type;
    private final Type genericType;
    private final Kind kind;
    private final int length;

    ValueCodec(Field field, DatabaseValue annotation) {
        this.type = field.getType();
        this.genericType = field.getGenericType();
        this.length = annotation.length();
        this.kind = annotation.serializeAsJson() ? Kind.JSON : kindOf(type);
    }

    private static Kind kindOf(Class<?> type) {
        if (type == String.class) return Kind.STRING;
        if (type == int.class || type == Integer.class) return Kind.INT;
        if (type == long.class || type == Long.class) return Kind.LONG;
        if (type == short.class || type == Short.class) return Kind.SHORT;
        if (type == byte.class || type == Byte.class) return Kind.BYTE;
        if (type == double.class || type == Double.class) return Kind.DOUBLE;
        if (type == float.class || type == Float.class) return Kind.FLOAT;
        if (type == boolean.class || type == Boolean.class) return Kind.BOOLEAN;
        if (type == byte[].class) return Kind.BYTES;
        if (type == UUID.class) return Kind.UUID;
        if (type.isEnum()) return Kind.ENUM;
        if (type == Timestamp.class) return Kind.TIMESTAMP;
        if (type == Date.class) return Kind.SQL_DATE;
        if (type == Instant.class) return Kind.INSTANT;
        if (type == LocalDateTime.class) return Kind.LOCAL_DATE_TIME;
        if (type == LocalDate.class) return Kind.LOCAL_DATE;
        return Kind.JSON;
    }

    /**
     * Whether the database can generate values of this column with AUTO_INCREMENT.
     */
    boolean isInteger() {
        return kind == Kind.INT || kind == Kind.LONG;
    }

    String sqlType() {
        return switch (kind) {
            case STRING -> length <= 255 ? "VARCHAR(" + Math.max(length, 1) + ")" : length <= 65_535 ? "TEXT" : "LONGTEXT";
            case INT -> "INT";
            case LONG -> "BIGINT";
            case SHORT -> "SMALLINT";
            case BYTE -> "TINYINT";
            case DOUBLE -> "DOUBLE";
            case FLOAT -> "FLOAT";
            case BOOLEAN -> "BOOLEAN";
            case BYTES -> "LONGBLOB";
            case UUID -> "VARCHAR(36)";
            // VARCHAR instead of ENUM(...), so adding a constant does not need a table change
            case ENUM -> "VARCHAR(" + Math.max(64, Arrays.stream(type.getEnumConstants())
                    .mapToInt(constant -> constant.toString().length()).max().orElse(0)) + ")";
            case TIMESTAMP, INSTANT, LOCAL_DATE_TIME -> "DATETIME(3)";
            case SQL_DATE, LOCAL_DATE -> "DATE";
            case JSON -> "LONGTEXT";
        };
    }

    /**
     * Field value to the value bound to a statement parameter.
     */
    Object encode(Object value) {
        if (value == null) {
            return null;
        }
        return switch (kind) {
            case UUID -> value.toString();
            // toString() like older versions, whose tables have ENUM(...) columns of these strings
            case ENUM -> value.toString();
            case INSTANT -> Timestamp.from((Instant) value);
            case LOCAL_DATE_TIME -> Timestamp.valueOf((LocalDateTime) value);
            case LOCAL_DATE -> Date.valueOf((LocalDate) value);
            case JSON -> GSON.toJson(value, genericType);
            default -> value;
        };
    }

    /**
     * Value read from a result set to the field value.
     */
    Object decode(Object value) {
        if (value == null) {
            return defaultValue();
        }
        return switch (kind) {
            case STRING -> text(value);
            case INT -> number(value).intValue();
            case LONG -> number(value).longValue();
            case SHORT -> number(value).shortValue();
            case BYTE -> number(value).byteValue();
            case DOUBLE -> number(value).doubleValue();
            case FLOAT -> number(value).floatValue();
            case BOOLEAN -> value instanceof Boolean bool ? bool
                    : value instanceof Number number ? number.intValue() != 0
                    : "1".equals(text(value)) || Boolean.parseBoolean(text(value));
            case BYTES -> bytes(value);
            case UUID -> value instanceof UUID uuid ? uuid : UUID.fromString(text(value));
            case ENUM -> enumConstant(text(value));
            case TIMESTAMP -> Timestamp.from(instant(value));
            case INSTANT -> instant(value);
            case LOCAL_DATE_TIME -> value instanceof LocalDateTime dateTime ? dateTime
                    : Timestamp.from(instant(value)).toLocalDateTime();
            case SQL_DATE -> Date.valueOf(localDate(value));
            case LOCAL_DATE -> localDate(value);
            case JSON -> json(value);
        };
    }

    private Object defaultValue() {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return switch (kind) {
            case INT -> 0;
            case LONG -> 0L;
            case SHORT -> (short) 0;
            case BYTE -> (byte) 0;
            case DOUBLE -> 0d;
            case FLOAT -> 0f;
            default -> null;
        };
    }

    private static Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        return new java.math.BigDecimal(text(value).trim());
    }

    private static String text(Object value) {
        try {
            if (value instanceof Clob clob) {
                return clob.getSubString(1, (int) clob.length());
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value.toString();
    }

    private static byte[] bytes(Object value) {
        try {
            if (value instanceof Blob blob) {
                return blob.getBytes(1, (int) blob.length());
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        return text(value).getBytes(StandardCharsets.UTF_8);
    }

    private Enum<?> enumConstant(String name) {
        for (var constant : type.getEnumConstants()) {
            var enumConstant = (Enum<?>) constant;
            // Written as toString(); the name is accepted too
            if (enumConstant.name().equals(name) || enumConstant.toString().equals(name)) {
                return enumConstant;
            }
        }
        throw new IllegalArgumentException("No " + type.getSimpleName() + " constant " + name);
    }

    private static Instant instant(Object value) {
        return switch (value) {
            case Timestamp timestamp -> timestamp.toInstant();
            case java.util.Date date -> date.toInstant();
            case Instant instant -> instant;
            case LocalDateTime dateTime -> Timestamp.valueOf(dateTime).toInstant();
            case OffsetDateTime dateTime -> dateTime.toInstant();
            case ZonedDateTime dateTime -> dateTime.toInstant();
            case LocalDate date -> Timestamp.valueOf(date.atStartOfDay()).toInstant();
            case Number millis -> Instant.ofEpochMilli(millis.longValue());
            default -> Timestamp.valueOf(text(value)).toInstant();
        };
    }

    private static LocalDate localDate(Object value) {
        return switch (value) {
            case Date date -> date.toLocalDate();
            case LocalDate date -> date;
            case LocalDateTime dateTime -> dateTime.toLocalDate();
            case String text -> LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
            default -> Timestamp.from(instant(value)).toLocalDateTime().toLocalDate();
        };
    }

    private Object json(Object value) {
        String json;
        if (value instanceof byte[] || value instanceof Blob) {
            var bytes = bytes(value);
            if (isJavaSerialized(bytes)) {
                return legacy(bytes);
            }
            json = new String(bytes, StandardCharsets.UTF_8);
        } else {
            json = text(value);
        }
        try {
            return GSON.fromJson(json, genericType);
        } catch (JsonParseException e) {
            throw new IllegalStateException("Invalid JSON for " + genericType.getTypeName() + ": " + e.getMessage(), e);
        }
    }

    private static boolean isJavaSerialized(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == JAVA_SERIALIZATION_MAGIC[0] && bytes[1] == JAVA_SERIALIZATION_MAGIC[1];
    }

    /**
     * Reads a value written with Java serialization by older versions. It is stored as JSON on the next update.
     */
    private Object legacy(byte[] bytes) {
        Object object;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(LEGACY_FILTER);
            object = in.readObject();
        } catch (InvalidClassException e) {
            throw new IllegalStateException("Refusing to read an old value of type " + e.classname
                    + "; only JDK collections and value types are allowed", e);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Could not read an old value: " + e.getMessage(), e);
        }
        if (object == null || type.isInstance(object)) {
            return object;
        }
        // e.g. an ArrayList stored for a field that is now a List<UUID>
        return GSON.fromJson(GSON.toJsonTree(object), genericType);
    }

    private static <T> TypeAdapter<T> isoAdapter(Function<String, T> parser) {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                } else {
                    out.value(value.toString());
                }
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                return parser.apply(in.nextString());
            }
        };
    }

    private static final class TimestampAdapter extends TypeAdapter<Timestamp> {
        @Override
        public void write(JsonWriter out, Timestamp value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.getTime());
            }
        }

        @Override
        public Timestamp read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return new Timestamp(in.nextLong());
        }
    }

}
