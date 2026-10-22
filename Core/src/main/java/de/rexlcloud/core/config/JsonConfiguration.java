package de.rexlcloud.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.rexlcloud.core.config.adapter.ServerGroupAdapter;
import de.rexlcloud.core.config.adapter.TypeAdapter;
import de.rexlcloud.core.config.adapter.UUIDTypeAdapter;
import de.rexlcloud.core.server.ServerGroup;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.UUID;

@Accessors(fluent = true)
public class JsonConfiguration {

    @Getter
    private static Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().setLenient()
            .registerTypeAdapter(ServerGroup.class, new ServerGroupAdapter())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .create();

    private static HashMap<Class<?>, TypeAdapter<?>> adapter = new HashMap<>();

    static {
        adapter.put(ServerGroup.class, new ServerGroupAdapter());
        adapter.put(UUID.class, new UUIDTypeAdapter());
    }

    public <T> boolean registerTypeAdapter(Class<T> clazz, TypeAdapter<T> typeAdapter){
        if (adapter.containsKey(clazz)) {
            return false;
        }
        adapter.put(clazz, typeAdapter);
        GsonBuilder gsonBuilder = new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().setLenient();
        adapter.forEach(gsonBuilder::registerTypeAdapter);
        GSON = gsonBuilder.create();
        return true;

    }

    public static <T> T loadOrCreateConfiguration(Path path, T configuration) {
        createConfiguration(path, configuration);
        return (T) loadConfiguration(path, configuration.getClass());
    }

    public static <T> T loadConfiguration(Path path, Class<T> defaultConfiguration) {
        try {
            return GSON.fromJson(Files.readString(path), defaultConfiguration);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> void createConfiguration(Path path, T configuration) {
        try {
            Files.writeString(path, GSON.toJson(configuration), StandardOpenOption.CREATE_NEW);
        } catch (IOException ignored) {
            // Configuration already exist
        }
    }

    public static <T> void saveConfiguration(Path path, T configuration) {
        try {
            Files.delete(path);
        } catch (Exception ignored) {
        }
        try {
            Files.writeString(path, GSON.toJson(configuration), StandardOpenOption.CREATE_NEW);
        } catch (IOException ignored) {
        }
    }

    public static void save(Path path, String json) {
        try {
            Files.delete(path);
        } catch (Exception ignored) {
        }
        try {
            Files.writeString(path, json, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> T load(Path path, Class<T> clazz) {
        try {
            return GSON.fromJson(Files.readString(path), clazz);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T load(Path path, Type type) {
        try {
            return GSON.fromJson(Files.readString(path), type);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean existsConfiguration(Path path) {
        return Files.exists(path);
    }

    public static void createFolder(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
