package de.rexlcloud.restmodule.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed access to a JSON request body. Missing or malformed fields end the request with 400.
 */
public class JsonBody {

    private final JsonObject json;

    private JsonBody(JsonObject json) {
        this.json = json;
    }

    public static JsonBody of(Context ctx) {
        var body = ctx.body();
        if (body.isBlank()) {
            return new JsonBody(new JsonObject());
        }
        try {
            var element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                throw ApiException.badRequest("The request body must be a JSON object");
            }
            return new JsonBody(element.getAsJsonObject());
        } catch (JsonParseException e) {
            throw ApiException.badRequest("The request body is not valid JSON");
        }
    }

    /**
     * @return the field as it is in the JSON (string, number, boolean, ...)
     */
    public JsonElement requireValue(String name) {
        var element = get(name);
        if (element == null || element.isJsonNull()) {
            throw ApiException.badRequest("The field '" + name + "' is required");
        }
        return element;
    }

    public String requireString(String name) {
        var value = optionalString(name);
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("The field '" + name + "' is required");
        }
        return value;
    }

    @Nullable
    public String optionalString(String name) {
        var element = get(name);
        if (element == null) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw ApiException.badRequest("The field '" + name + "' must be a string");
        }
        return element.getAsString();
    }

    public int requireInt(String name) {
        var value = optionalInt(name);
        if (value == null) {
            throw ApiException.badRequest("The field '" + name + "' is required");
        }
        return value;
    }

    @Nullable
    public Integer optionalInt(String name) {
        var element = get(name);
        if (element == null) {
            return null;
        }
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                var number = element.getAsBigDecimal();
                return number.intValueExact();
            }
        } catch (ArithmeticException ignored) {
        }
        throw ApiException.badRequest("The field '" + name + "' must be a whole number");
    }

    public boolean requireBoolean(String name) {
        var value = optionalBoolean(name);
        if (value == null) {
            throw ApiException.badRequest("The field '" + name + "' is required");
        }
        return value;
    }

    @Nullable
    public Boolean optionalBoolean(String name) {
        var element = get(name);
        if (element == null) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw ApiException.badRequest("The field '" + name + "' must be true or false");
        }
        return element.getAsBoolean();
    }

    @Nullable
    public List<String> optionalStringList(String name) {
        var element = get(name);
        if (element == null) {
            return null;
        }
        if (!element.isJsonArray()) {
            throw ApiException.badRequest("The field '" + name + "' must be a list");
        }
        var list = new ArrayList<String>();
        for (var entry : element.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw ApiException.badRequest("The field '" + name + "' must only contain strings");
            }
            list.add(entry.getAsString());
        }
        return list;
    }

    @Nullable
    private JsonElement get(String name) {
        var element = json.get(name);
        return element == null || element.isJsonNull() ? null : element;
    }

}
