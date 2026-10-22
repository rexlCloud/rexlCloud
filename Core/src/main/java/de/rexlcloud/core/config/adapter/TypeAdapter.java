package de.rexlcloud.core.config.adapter;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

public interface TypeAdapter<T> extends JsonDeserializer<T>, JsonSerializer<T> {
}
