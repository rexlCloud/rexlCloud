package de.rexlcloud.core.config.adapter;

import com.google.gson.*;
import de.rexlcloud.core.config.MessageKey;

import java.lang.reflect.Type;

public class MessageKeyAdapter implements TypeAdapter<MessageKey> {
    @Override
    public MessageKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return MessageKey.valueOf(json.toString().toUpperCase());
    }

    @Override
    public JsonElement serialize(MessageKey src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.name().toLowerCase());
    }
}
