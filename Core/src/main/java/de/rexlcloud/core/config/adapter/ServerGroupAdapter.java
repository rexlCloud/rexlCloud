package de.rexlcloud.core.config.adapter;

import com.google.gson.*;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.ServerType;

import java.lang.reflect.Type;

public class ServerGroupAdapter implements TypeAdapter<ServerGroup> {

    @Override
    public ServerGroup deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        var jsonObject = json.getAsJsonObject();
        var name = jsonObject.get("name").getAsString();
        var memory = jsonObject.get("memory").getAsInt();
        var type = ServerType.valueOf(jsonObject.get("type").getAsString());
        var minServers = jsonObject.get("minServer").getAsInt();
        var fallback = type != ServerType.PROXY && (jsonObject.has("isFallback") && jsonObject.get("isFallback").getAsBoolean());
        var deleteLogs = jsonObject.has("deleteOldLogs") && jsonObject.get("deleteOldLogs").getAsBoolean();
        var javaArguments = jsonObject.get("javaArgs").getAsString();
        var staticServer = jsonObject.get("static").getAsBoolean();
        var wrapper = staticServer ? jsonObject.get("wrapper").getAsString() : null;
        var maxPlayers = jsonObject.get("maxPlayers").getAsInt();
        var javaCommand = jsonObject.get("javaCommand").getAsString();
        var group = new ServerGroup(name, type, memory, staticServer, wrapper, minServers, fallback, deleteLogs, javaArguments, maxPlayers, javaCommand);
        group.setCustomJar(jsonObject.has("customJar") && jsonObject.get("customJar").getAsBoolean());
        return group;
    }

    @Override
    public JsonElement serialize(ServerGroup serverGroup, Type typeOfSrc, JsonSerializationContext context) {
        var object = new JsonObject();
        object.addProperty("name", serverGroup.getName());
        object.addProperty("memory", serverGroup.getMemory());
        object.addProperty("type", serverGroup.getType().name());
        object.addProperty("minServer", serverGroup.getMinServers());
        object.addProperty("deleteOldLogs", serverGroup.isRemoveOldLogs());
        object.addProperty("javaArgs", serverGroup.getJavaArguments());
        object.addProperty("maxPlayers", serverGroup.getMaxPlayers());
        object.addProperty("javaCommand", serverGroup.getJavaCommand());
        if (serverGroup.getType() != ServerType.PROXY) {
            object.addProperty("isFallback", serverGroup.isFallback());
        }
        object.addProperty("static", serverGroup.isStaticServer());
        if (serverGroup.isCustomJar()) {
            object.addProperty("customJar", true);
        }
        if (serverGroup.isStaticServer()) {
            object.addProperty("wrapper", serverGroup.getWrapper());
        }
        return object;
    }
}
