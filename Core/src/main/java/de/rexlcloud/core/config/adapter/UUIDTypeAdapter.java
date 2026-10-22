package de.rexlcloud.core.config.adapter;

import com.google.gson.*;
import de.rexlcloud.core.config.JsonConfiguration;

import java.lang.reflect.Type;
import java.util.UUID;

public class UUIDTypeAdapter implements TypeAdapter<UUID> {

    @Override
    public UUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return UUID.fromString(json.getAsString());
    }

    @Override
    public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
        return JsonConfiguration.GSON().toJsonTree(src.toString());
    }
}
