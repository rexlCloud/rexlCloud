package de.rexlcloud.core.api.module;

import de.rexlcloud.core.api.events.EventManager;
import de.rexlcloud.core.command.CommandManager;
import de.rexlcloud.core.server.files.ServerJarManager;
import lombok.Getter;

import java.io.File;

/**
 * Base class of a module: an extension that runs inside the master, e.g. the RestModule.
 * <p>
 * A module is a jar in the master's {@code modules/} folder with a {@code module.json} that names its main class,
 * which extends this class:
 * <pre>{@code
 * {"name": "MyModule", "main": "com.example.MyModule", "author": "me", "version": "1.0"}
 * }</pre>
 * Through the getters it reaches the cloud: {@link #getModuleApi()} to read and change the network,
 * {@link #getEventManager()} for the cloud's events and {@link #getCommandManager()} for console commands.
 */
public abstract class CloudModule {

    /**
     * What the module's {@code module.json} says.
     */
    @Getter
    private ModuleInfo moduleInfo;
    /**
     * The module's own folder for its files, {@code modules/<name>/}.
     */
    @Getter
    private File dataFolder;
    /**
     * The cloud's events (see {@link de.rexlcloud.core.api.events.Event}).
     */
    @Getter
    private EventManager eventManager;
    /**
     * Registers console commands of the module.
     */
    @Getter
    private CommandManager commandManager;
    /**
     * The server jars the cloud can install.
     */
    @Getter
    private ServerJarManager serverJarManager;
    /**
     * Reads and changes servers, groups, players, wrappers and more.
     */
    @Getter
    private ModuleApi moduleApi;

    /**
     * Called when the master loads the module.
     */
    public abstract void onEnable();

    /**
     * Called when the module is disabled or the master stops.
     */
    public abstract void onDisable();

    /**
     * Called when the module is reloaded with {@code module reload}.
     */
    public abstract void onReload();

    /**
     * Internal: called by the master to start the module.
     */
    protected final void enable(
            ModuleInfo moduleInfo,
            File dataFolder,
            EventManager eventManager,
            CommandManager commandManager,
            ServerJarManager serverJarManager,
            ModuleApi moduleApi
    ) {
        if (this.dataFolder != null) {
            return;
        }
        this.moduleInfo = moduleInfo;
        this.dataFolder = dataFolder;
        this.eventManager = eventManager;
        this.commandManager = commandManager;
        this.serverJarManager = serverJarManager;
        this.moduleApi = moduleApi;
        onEnable();
    }

}
