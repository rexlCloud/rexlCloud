package de.rexlcloud.core.api.module;

import com.google.gson.JsonParser;
import de.rexlcloud.core.api.events.EventManager;
import de.rexlcloud.core.command.CommandManager;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.server.files.ServerJarManager;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

/**
 * Internal: loads a module jar.
 */
@Getter
public class ModuleClassLoader extends URLClassLoader {

    private CloudModule cloudModule;
    private final ModuleInfo moduleInfo;
    private final ClassLoader classLoader;

    public ModuleClassLoader(@NonNull File file, @NonNull ClassLoader parent) throws MalformedURLException {
        super(new URL[]{file.toURI().toURL()}, parent);
        this.classLoader = parent;
        this.moduleInfo = getModuleInfo(file);
        if (moduleInfo == null) {
            Console.error("Could not load module.json from " + file.getName());
            return;
        }
        try {
            var mainClass = Class.forName(moduleInfo.getMainClass(), true, this).asSubclass(CloudModule.class);
            this.cloudModule = mainClass.newInstance();
        } catch (ClassNotFoundException | ClassCastException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    public void enable(ModuleInfo moduleInfo, File dataFolder, EventManager eventManager, CommandManager commandManager, ServerJarManager serverJarManager, ModuleApi moduleApi) {
        cloudModule.enable(moduleInfo, dataFolder, eventManager, commandManager, serverJarManager, moduleApi);
    }

    @Nullable
    private ModuleInfo getModuleInfo(@NonNull File file) {
        try {
            var jarFile = new JarFile(file);
            var jarEntry = jarFile.getJarEntry("module.json");
            var fileInputStream = jarFile.getInputStream(jarEntry);
            var br = new BufferedReader(new InputStreamReader(fileInputStream));
            String output;
            var output2 = new StringBuilder();
            while ((output = br.readLine()) != null) {
                output2.append(output);
            }
            br.close();
            try {
                var jsonObject = JsonParser.parseReader(new StringReader(output2.toString())).getAsJsonObject();
                var name = jsonObject.get("name").getAsString();
                var version = jsonObject.get("version").getAsString();
                var main = jsonObject.get("main").getAsString();
                var author = jsonObject.get("author").getAsString();
                if (name != null && version != null && main != null && author != null) {
                    return new ModuleInfo(name, main, version, author);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                jarFile.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

}
