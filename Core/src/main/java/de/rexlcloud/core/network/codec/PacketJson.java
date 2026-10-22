package de.rexlcloud.core.network.codec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.rexlcloud.core.api.exceptions.CloudException;
import de.rexlcloud.core.config.adapter.ServerGroupAdapter;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.api.ApiRequestPacket;
import de.rexlcloud.core.network.packets.api.ApiRequestPacketToSpecific;
import de.rexlcloud.core.network.packets.api.ApiResponsePacket;
import de.rexlcloud.core.network.packets.api.ApiUpdateObjectPacket;
import de.rexlcloud.core.server.ServerGroup;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Converts {@link PacketContainer}s to JSON and back. This replaces Java serialization on the connections between
 * master, wrappers and servers: reading a message only fills the fields of rexlCloud's own classes, so a message
 * can never make the receiver run code.
 * <p>
 * Values whose type is not fixed by a field (the payload of a container, API request arguments and responses) are
 * written with their type, e.g. {@code {"t":"obj","c":"de.rexlcloud.core.api.Server","v":{...}}}. Only strings,
 * numbers, booleans, UUIDs, byte arrays, enums, lists, maps, arrays and classes of {@code de.rexlcloud.} are accepted.
 */
public final class PacketJson {

    private static final String ALLOWED_PACKAGE = "de.rexlcloud.";
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeSpecialFloatingPointValues()
            .registerTypeAdapter(byte[].class, new Base64Adapter().nullSafe())
            .registerTypeAdapter(ServerGroup.class, new ServerGroupAdapter())
            .registerTypeAdapter(ApiRequestPacket.class, requestAdapter())
            .registerTypeAdapter(ApiRequestPacketToSpecific.class, specificRequestAdapter())
            .registerTypeAdapter(ApiResponsePacket.class, responseAdapter())
            .registerTypeAdapter(ApiUpdateObjectPacket.class, updateAdapter())
            .create();

    private PacketJson() {
    }

    public static String write(PacketContainer container) {
        var json = new JsonObject();
        json.addProperty("type", container.getPacketType() == null ? null : container.getPacketType().name());
        json.add("packet", writeValue(container.getPacket()));
        return GSON.toJson(json);
    }

    /**
     * @throws JsonParseException for malformed messages or types that are not allowed
     */
    public static PacketContainer read(String text) {
        var json = JsonParser.parseString(text).getAsJsonObject();
        var type = json.get("type");
        PacketType packetType = null;
        if (type != null && !type.isJsonNull()) {
            try {
                packetType = PacketType.valueOf(type.getAsString());
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("Unknown packet type " + type.getAsString());
            }
        }
        return new PacketContainer(packetType, readValue(json.get("packet")));
    }

    // Values with their type

    static JsonElement writeValue(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        var json = new JsonObject();
        switch (value) {
            case String string -> tagged(json, "str", new JsonPrimitive(string));
            case Integer number -> tagged(json, "int", new JsonPrimitive(number));
            case Long number -> tagged(json, "long", new JsonPrimitive(number));
            case Double number -> tagged(json, "double", GSON.toJsonTree(number));
            case Float number -> tagged(json, "float", GSON.toJsonTree(number));
            case Short number -> tagged(json, "short", new JsonPrimitive(number));
            case Byte number -> tagged(json, "byte", new JsonPrimitive(number));
            case Boolean bool -> tagged(json, "bool", new JsonPrimitive(bool));
            case Character character -> tagged(json, "char", new JsonPrimitive(character));
            case UUID uuid -> tagged(json, "uuid", new JsonPrimitive(uuid.toString()));
            case byte[] bytes -> tagged(json, "bytes", new JsonPrimitive(Base64.getEncoder().encodeToString(bytes)));
            case Enum<?> constant -> {
                json.addProperty("c", constant.getDeclaringClass().getName());
                tagged(json, "enum", new JsonPrimitive(constant.name()));
            }
            case Throwable throwable -> {
                json.addProperty("c", throwable.getClass().getName());
                tagged(json, "error", throwable.getMessage() == null ? JsonNull.INSTANCE : new JsonPrimitive(throwable.getMessage()));
            }
            case Collection<?> collection -> {
                var array = new JsonArray();
                collection.forEach(element -> array.add(writeValue(element)));
                tagged(json, "list", array);
            }
            case Map<?, ?> map -> {
                var array = new JsonArray();
                map.forEach((key, element) -> {
                    var entry = new JsonArray();
                    entry.add(writeValue(key));
                    entry.add(writeValue(element));
                    array.add(entry);
                });
                tagged(json, "map", array);
            }
            default -> {
                if (value.getClass().isArray()) {
                    var array = new JsonArray();
                    for (int i = 0; i < Array.getLength(value); i++) {
                        array.add(writeValue(Array.get(value, i)));
                    }
                    json.addProperty("c", value.getClass().getComponentType().getName());
                    tagged(json, "array", array);
                } else {
                    var type = value.getClass();
                    if (!type.getName().startsWith(ALLOWED_PACKAGE)) {
                        throw new JsonParseException("Cannot send " + type.getName() + " to another cloud component");
                    }
                    json.addProperty("c", type.getName());
                    tagged(json, "obj", GSON.toJsonTree(value, type));
                }
            }
        }
        return json;
    }

    private static void tagged(JsonObject json, String tag, JsonElement value) {
        json.addProperty("t", tag);
        json.add("v", value);
    }

    static Object readValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        var json = element.getAsJsonObject();
        var tag = json.get("t").getAsString();
        var value = json.get("v");
        return switch (tag) {
            case "str" -> value.getAsString();
            case "int" -> value.getAsInt();
            case "long" -> value.getAsLong();
            case "double" -> GSON.fromJson(value, Double.class);
            case "float" -> GSON.fromJson(value, Float.class);
            case "short" -> value.getAsShort();
            case "byte" -> value.getAsByte();
            case "bool" -> value.getAsBoolean();
            case "char" -> value.getAsString().charAt(0);
            case "uuid" -> UUID.fromString(value.getAsString());
            case "bytes" -> Base64.getDecoder().decode(value.getAsString());
            case "enum" -> enumConstant(allowedClass(json.get("c").getAsString()), value.getAsString());
            case "error" -> error(json.get("c").getAsString(), value.isJsonNull() ? null : value.getAsString());
            case "list" -> {
                var list = new ArrayList<>();
                value.getAsJsonArray().forEach(item -> list.add(readValue(item)));
                yield list;
            }
            case "map" -> {
                var map = new LinkedHashMap<>();
                value.getAsJsonArray().forEach(entry -> map.put(readValue(entry.getAsJsonArray().get(0)),
                        readValue(entry.getAsJsonArray().get(1))));
                yield map;
            }
            case "array" -> {
                var items = value.getAsJsonArray();
                var array = Array.newInstance(componentClass(json.get("c").getAsString()), items.size());
                for (int i = 0; i < items.size(); i++) {
                    Array.set(array, i, readValue(items.get(i)));
                }
                yield array;
            }
            case "obj" -> GSON.fromJson(value, allowedClass(json.get("c").getAsString()));
            default -> throw new JsonParseException("Unknown value type " + tag);
        };
    }

    /**
     * Classes of rexlCloud only; loading does not run their static initializers.
     */
    private static Class<?> allowedClass(String name) {
        if (!name.startsWith(ALLOWED_PACKAGE)) {
            throw new JsonParseException("Refusing to read " + name);
        }
        try {
            return Class.forName(name, false, PacketJson.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new JsonParseException("Unknown class " + name);
        }
    }

    private static Class<?> componentClass(String name) {
        return switch (name) {
            case "java.lang.Object" -> Object.class;
            case "java.lang.String" -> String.class;
            case "java.lang.Integer" -> Integer.class;
            case "java.lang.Long" -> Long.class;
            case "java.lang.Double" -> Double.class;
            case "java.lang.Boolean" -> Boolean.class;
            case "java.util.UUID" -> UUID.class;
            case "java.io.Serializable" -> Serializable.class;
            case "[B" -> byte[].class;
            default -> allowedClass(name);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name) {
        if (!type.isEnum()) {
            throw new JsonParseException(type.getName() + " is no enum");
        }
        return Enum.valueOf((Class<Enum>) type, name);
    }

    /**
     * Rebuilds an exception of rexlCloud with its type and message, or a RuntimeException for other types.
     */
    private static Throwable error(String className, String message) {
        if (className.startsWith(ALLOWED_PACKAGE)) {
            try {
                var type = Class.forName(className, false, PacketJson.class.getClassLoader());
                if (Throwable.class.isAssignableFrom(type)) {
                    Throwable error;
                    try {
                        error = (Throwable) type.getConstructor(String.class).newInstance(message);
                    } catch (NoSuchMethodException e) {
                        error = (Throwable) type.getConstructor().newInstance();
                    }
                    // Constructors like ServerNotFoundException(name) build their own message
                    if (error instanceof CloudException cloudException) {
                        cloudException.restoreMessage(message);
                    }
                    return error;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall back below
            }
        }
        return new RuntimeException(message);
    }

    // Packets whose fields can hold any value

    private static <T> JsonSerializer<T> serializer(BiConsumer<T, JsonObject> writer) {
        return (packet, type, context) -> {
            var json = new JsonObject();
            writer.accept(packet, json);
            return json;
        };
    }

    private static JsonSerializer<ApiRequestPacket<?>> requestSerializer() {
        return serializer((packet, json) -> {
            json.addProperty("requestId", packet.getRequestId());
            json.addProperty("server", packet.getServer());
            json.add("data", writeValue(packet.getData()));
        });
    }

    private static Object requestAdapter() {
        return new Adapter<ApiRequestPacket<?>>(requestSerializer(), (json, type, context) -> {
            var object = json.getAsJsonObject();
            return new ApiRequestPacket<>(object.get("requestId").getAsInt(), string(object, "server"),
                    (Object[]) readValue(object.get("data")));
        });
    }

    private static Object specificRequestAdapter() {
        JsonSerializer<ApiRequestPacketToSpecific<?>> serializer = (packet, type, context) -> {
            var json = requestSerializer().serialize(packet, type, context).getAsJsonObject();
            json.addProperty("targetServer", packet.getTargetServerName());
            return json;
        };
        return new Adapter<ApiRequestPacketToSpecific<?>>(serializer, (json, type, context) -> {
            var object = json.getAsJsonObject();
            return new ApiRequestPacketToSpecific<>(object.get("requestId").getAsInt(), string(object, "server"),
                    (Object[]) readValue(object.get("data")), string(object, "targetServer"));
        });
    }

    private static Object responseAdapter() {
        return new Adapter<ApiResponsePacket>(serializer((packet, json) -> {
            json.addProperty("requestId", packet.getRequestId());
            json.addProperty("server", packet.getServer());
            json.add("response", writeValue(packet.getResponse()));
        }), (json, type, context) -> {
            var object = json.getAsJsonObject();
            return new ApiResponsePacket(object.get("requestId").getAsInt(), string(object, "server"),
                    readValue(object.get("response")));
        });
    }

    private static Object updateAdapter() {
        return new Adapter<ApiUpdateObjectPacket>(serializer((packet, json) -> {
            json.addProperty("targetServer", packet.getTargetServer());
            json.addProperty("name", packet.getName());
            json.add("object", writeValue(packet.getObject()));
        }), (json, type, context) -> {
            var object = json.getAsJsonObject();
            return new ApiUpdateObjectPacket(string(object, "targetServer"), string(object, "name"),
                    readValue(object.get("object")));
        });
    }

    private static String string(JsonObject object, String name) {
        var value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /**
     * A serializer and deserializer registered as one type adapter.
     */
    private record Adapter<T>(JsonSerializer<T> serializer, JsonDeserializer<T> deserializer)
            implements JsonSerializer<T>, JsonDeserializer<T> {

        @Override
        public JsonElement serialize(T src, Type typeOfSrc, JsonSerializationContext context) {
            return serializer.serialize(src, typeOfSrc, context);
        }

        @Override
        public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return deserializer.deserialize(json, typeOfT, context);
        }
    }

    /**
     * byte[] as Base64 instead of an array of numbers, so a 1 MB file chunk stays about 1.3 MB.
     */
    private static final class Base64Adapter extends TypeAdapter<byte[]> {
        @Override
        public void write(JsonWriter out, byte[] value) throws IOException {
            out.value(Base64.getEncoder().encodeToString(value));
        }

        @Override
        public byte[] read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Base64.getDecoder().decode(in.nextString());
        }
    }

    /**
     * Only for tests: converts a value there and back.
     */
    static Object roundTrip(Object value) {
        return readValue(JsonParser.parseString(GSON.toJson(writeValue(value))));
    }

}
