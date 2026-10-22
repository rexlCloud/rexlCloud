package de.rexlcloud.master.server;

import com.google.gson.reflect.TypeToken;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.InstallServerJarPacket;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.core.server.files.ServerJar;
import de.rexlcloud.core.server.files.ServerJarInstaller;
import de.rexlcloud.master.RexlCloudMaster;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;

public class ServerGroupManager {

    @Getter
    private final ArrayList<ServerGroup> serverGroups;
    private Path configPath = Path.of("config");

    public ServerGroupManager() {
        JsonConfiguration.createFolder(configPath);
        configPath = configPath.resolve("groups.json");
        if (!JsonConfiguration.existsConfiguration(configPath)) {
            this.serverGroups = new ArrayList<>();
            JsonConfiguration.save(configPath, JsonConfiguration.GSON().toJson(serverGroups));
        } else {
            this.serverGroups = JsonConfiguration.load(configPath, new TypeToken<ArrayList<ServerGroup>>() {
            }.getType());

            this.serverGroups.forEach(serverGroup -> {
                if (!serverGroup.isStaticServer()) {
                    var file = new File("templates/" + serverGroup.getName() + "/default");
                    file.mkdirs();
                }
                var amount = serverGroup.isStaticServer() ? 1 : serverGroup.getMinServers();
                for (int i = 0; i < amount; i++) {
                    var server = new Server(RexlCloudMaster.getInstance().getServerManager().getServerNameForNextServer(serverGroup), serverGroup);
                    RexlCloudMaster.getInstance().getServerManager().startServer(server);
                }
            });
        }
    }

    /**
     * Creates and saves a group, installs its server jar and starts its first servers.
     * For non-static groups the jar is downloaded (or built) on the calling thread, and no group is created if
     * that fails.
     *
     * @param wrapper    wrapper the static server runs on, ignored for non-static groups
     * @param minServers servers to keep online, ignored for static groups
     */
    public ServerGroup createServerGroup(String name, ServerType type, int memory, boolean staticServer,
                                         @Nullable String wrapper, int minServers, boolean fallback,
                                         ServerJar serverJar) {
        var serverManager = RexlCloudMaster.getInstance().getServerManager();
        var custom = serverJar.getSource() == ServerJar.Source.CUSTOM;
        if (staticServer) {
            var serverGroup = new ServerGroup(name, type, memory, true, wrapper, 1, fallback, false, "",
                    100, "java");
            serverGroup.setCustomJar(custom);
            addServerGroup(serverGroup);
            if (custom) {
                Console.info("Put the server.jar of %s into servers/templates/%s/default/ on the wrapper %s", name,
                        name, wrapper);
            } else {
                installServerJar(serverGroup, serverJar);
            }
            serverManager.startServer(new Server(serverManager.getServerNameForNextServer(serverGroup), serverGroup));
            return serverGroup;
        }

        var serverGroup = new ServerGroup(name, type, memory, false, null, minServers, fallback, false, "",
                100, "java");
        serverGroup.setCustomJar(custom);
        var outputFile = new File(serverGroup.getDefaultTemplateFolder(),
                type == ServerType.SERVER ? "server.jar" : "proxy.jar");
        if (custom) {
            // Its servers wait until the jar is there
            outputFile.getParentFile().mkdirs();
            Console.info("Put the server.jar of %s into %s", name, outputFile.getParentFile().getPath());
        } else {
            try {
                // Building Spigot takes a few minutes, downloads are quick
                ServerJarInstaller.inWorkingDirectory().install(serverJar, outputFile.toPath());
            } catch (IOException e) {
                throw new UncheckedIOException("Could not install " + serverJar.getFullText() + ": " + e.getMessage(), e);
            }
        }

        addServerGroup(serverGroup);
        // Sends the new template (with the jar) to the wrappers before any server of the group starts
        RexlCloudMaster.getInstance().getTemplateSync().sync(serverGroup.getName());
        for (int i = 0; i < minServers; i++) {
            serverManager.startServer(new Server(serverManager.getServerNameForNextServer(serverGroup), serverGroup));
        }
        return serverGroup;
    }

    public void installServerJar(ServerGroup serverGroup, ServerJar serverJar) {
        var installServerJarPacket = new PacketContainer(PacketType.INSTALL_SERVER_JAR, new InstallServerJarPacket(serverGroup, serverJar));
        RexlCloudMaster.getInstance().getWrapperManager().getWrapperByName(serverGroup.getWrapper()).sendPacket(installServerJarPacket);
    }

    public void addServerGroup(ServerGroup serverGroup) {
        serverGroups.add(serverGroup);
        save();
    }

    public void save() {
        JsonConfiguration.save(configPath, JsonConfiguration.GSON().toJson(serverGroups));
    }

    public void removeServerGroup(ServerGroup serverGroup) {
        serverGroups.remove(serverGroup);
        save();
        RexlCloudMaster.getInstance().getTemplateSync().removeDeletedTemplates();
    }

    public boolean existServerGroup(String name) {
        return getServerGroupByName(name).isPresent();
    }

    public Optional<ServerGroup> getServerGroupByName(String name) {
        return serverGroups.stream().filter(serverGroup -> serverGroup.getName().equals(name)).findFirst();
    }

}
