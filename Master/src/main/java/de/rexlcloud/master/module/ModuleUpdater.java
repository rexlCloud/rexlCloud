package de.rexlcloud.master.module;

import com.google.gson.JsonParser;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.release.CloudReleases;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Brings the official modules in the modules folder to the master's version before they are loaded, because a
 * module only works with the master it was built for. Only modules that are installed are updated: removing a
 * module's jar keeps it removed. A module that cannot be updated stays as it is.
 */
public class ModuleUpdater {

    private final Path modulesFolder;
    private final CloudReleases releases;

    /**
     * Downloads from the cloud's GitHub releases.
     */
    public ModuleUpdater(Path modulesFolder) {
        this(modulesFolder, CloudReleases.official());
    }

    ModuleUpdater(Path modulesFolder, CloudReleases releases) {
        this.modulesFolder = modulesFolder;
        this.releases = releases;
    }

    record ModuleInfo(String name, String version) {
    }

    public void update(String masterVersion) {
        if (masterVersion == null || !masterVersion.matches("\\d+(\\.\\d+)*")) {
            // Started from a development build, there is no matching release
            return;
        }
        List<Path> jars;
        try (var files = Files.list(modulesFolder)) {
            jars = files.filter(file -> file.getFileName().toString().endsWith(".jar")).toList();
        } catch (IOException e) {
            return;
        }
        for (var jar : jars) {
            var info = readModuleInfo(jar);
            if (info == null) {
                continue;
            }
            var official = OfficialModule.ALL.stream().filter(module -> module.name().equals(info.name())).findFirst();
            if (official.isEmpty() || masterVersion.equals(info.version())) {
                continue;
            }
            try {
                var target = modulesFolder.resolve(info.name() + "-" + masterVersion + ".jar");
                Console.info("Updating the module %s from %s to %s", info.name(), info.version(), masterVersion);
                releases.download(masterVersion, official.get().artifact(), target);
                if (!target.equals(jar)) {
                    Files.deleteIfExists(jar);
                }
            } catch (IOException e) {
                Console.warn("The module %s could not be updated and stays on %s: %s", info.name(), info.version(),
                        describe(e));
            }
        }
    }

    /**
     * Whether a jar of the module is in the modules folder, in any version.
     */
    public boolean isInstalled(OfficialModule module) {
        try (var files = Files.list(modulesFolder)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".jar"))
                    .map(ModuleUpdater::readModuleInfo)
                    .anyMatch(info -> info != null && info.name().equals(module.name()));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Downloads an official module in the given version into the modules folder. It is loaded on the next start or
     * with {@code modules load}.
     *
     * @param version the master's version; development builds have no release to download
     * @return the jar
     * @throws IOException if there is no release of this version or the download fails
     */
    public Path install(OfficialModule module, String version) throws IOException {
        if (version == null || !version.matches("\\d+(\\.\\d+)*")) {
            throw new IOException("this master is a development build (version " + version + "), which has no release"
                    + " to download the module from; copy its jar into " + modulesFolder + " by hand");
        }
        Files.createDirectories(modulesFolder);
        var target = modulesFolder.resolve(module.name() + "-" + version + ".jar");
        try {
            releases.download(version, module.artifact(), target);
        } catch (IOException first) {
            // Once more for a dropped connection; a missing release fails the same way again
            Console.warn("Downloading the %s failed (%s), trying again", module.name(), describe(first));
            releases.download(version, module.artifact(), target);
        }
        return target;
    }

    /**
     * The message of a failed download; some network errors have none, then their type and cause say more.
     */
    public static String describe(IOException e) {
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        var cause = e.getCause();
        return e.getClass().getSimpleName() + (cause == null ? "" : " (" + cause + ")");
    }

    /**
     * Name and version from the module.json in a module jar, or {@code null} if it has none.
     */
    static ModuleInfo readModuleInfo(Path jar) {
        try (var zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("module.json");
            if (entry == null) {
                return null;
            }
            try (var in = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                var json = JsonParser.parseReader(in).getAsJsonObject();
                return new ModuleInfo(json.get("name").getAsString(), json.get("version").getAsString());
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

}
