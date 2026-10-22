package de.rexlcloud.core.command;

import de.rexlcloud.core.api.module.CloudModule;
import lombok.Getter;
import lombok.NonNull;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Registers console commands of modules; get it with
 * {@link de.rexlcloud.core.api.module.CloudModule#getCommandManager()}.
 * <pre>{@code
 * public class MyCommands {
 *     @CommandMethod("greet <player>")
 *     @CommandDescription("Greets a player")
 *     public void greet(@Argument(value = "player", suggestions = "players") String player) {
 *         Console.info("Hello %s", player);
 *     }
 *
 *     @Suggestion("players")
 *     public List<String> players() {
 *         return List.of("Notch", "jeb_");
 *     }
 * }
 *
 * getCommandManager().registerCommands(this, new MyCommands());
 * }</pre>
 */
public class CommandManager {

    private final CommandRegistry commandRegistry;
    /**
     * The command objects each module registered.
     */
    @Getter
    private final HashMap<CloudModule, ArrayList<Object>> registeredCommands;

    /**
     * Internal: created by the master.
     */
    protected CommandManager(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
        this.registeredCommands = new HashMap<>();
    }

    /**
     * Registers the {@link CommandMethod}, {@link Suggestion}, {@link CommandPreProcessor} and {@link ObjectParser}
     * methods of an object.
     *
     * @param cloudModule the module, for unregistering its commands when it is disabled
     * @param object      the object with the annotated methods
     */
    public void registerCommands(@NonNull CloudModule cloudModule, @NonNull Object object) {
        if (!registeredCommands.containsKey(cloudModule)) {
            registeredCommands.put(cloudModule, new ArrayList<>());
        }
        registeredCommands.get(cloudModule).add(object);
        commandRegistry.addCommand(object);
    }

    /**
     * Removes all commands of a module.
     */
    public void unregisterAllCommands(@NonNull CloudModule cloudModule) {
        if (registeredCommands.containsKey(cloudModule)) {
            new ArrayList<>(registeredCommands.get(cloudModule))
                    .forEach(object -> unregisterCommands(cloudModule, object));
        }
    }

    /**
     * Removes the commands of one object.
     */
    public void unregisterCommands(@NonNull CloudModule cloudModule, @NonNull Object object) {
        var removedCommands = commandRegistry.getCommands().stream()
                .filter(commandMethodePair -> commandMethodePair.getCommandInstance().equals(object))
                .toList();
        commandRegistry.getCommands().removeAll(removedCommands);

        var removeProcessors = commandRegistry.getCommandPreprocessors().stream()
                .filter(methodObjectEntry -> methodObjectEntry.getValue().equals(object))
                .toList();
        commandRegistry.getCommandPreprocessors().removeAll(removeProcessors);

        var removeSuggestions = commandRegistry.getSuggestions().stream()
                .filter(suggestionMethodePair -> suggestionMethodePair.getCommandInstance().equals(object))
                .toList();
        commandRegistry.getSuggestions().removeAll(removeSuggestions);

        commandRegistry.getObjectParsers().entrySet().stream().
                        <Map.Entry<Class<?>, Map.Entry<Method, Object>>>mapMulti((entry, consumer) ->
                        entry.getValue().forEach(val -> consumer.accept(new AbstractMap.SimpleEntry<>(entry.getKey(), val))))
                .filter(classEntryEntry -> classEntryEntry.getValue().getValue().equals(object))
                .forEach(classEntryEntry -> commandRegistry.getObjectParsers().get(classEntryEntry.getKey()).remove(classEntryEntry.getValue()));

        if (registeredCommands.containsKey(cloudModule)) {
            registeredCommands.get(cloudModule).remove(object);
        }
    }

}
