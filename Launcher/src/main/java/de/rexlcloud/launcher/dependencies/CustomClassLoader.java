package de.rexlcloud.launcher.dependencies;

import lombok.Getter;
import lombok.NonNull;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

@Getter
public class CustomClassLoader extends URLClassLoader {

    public CustomClassLoader(@NonNull File file, @NonNull ClassLoader parent, String mainClassName, String[] args) throws MalformedURLException {
        super(new URL[]{file.toURI().toURL()}, parent);
        try {
            var mainClass = Class.forName(mainClassName, true, this);
            mainClass.getConstructor(String[].class).newInstance((Object) args);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
