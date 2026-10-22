package de.rexlcloud.core.command;

import de.rexlcloud.core.console.Console;
import lombok.Getter;
import lombok.NonNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

@Getter
public class CommandRegistry {

    private final ArrayList<CommandMethodPair> commands;
    private final ArrayList<SuggestionMethodePair> suggestions;
    private final ArrayList<Map.Entry<Method, Object>> commandPreprocessors;
    private final HashMap<Class<?>, ArrayList<Map.Entry<Method, Object>>> objectParsers;

    public CommandRegistry() {
        commands = new ArrayList<>();
        suggestions = new ArrayList<>();
        commandPreprocessors = new ArrayList<>();
        objectParsers = new HashMap<>();
        commandManager = new CommandManager(this);
    }

    private final CommandManager commandManager;

    /**
     * The command of a method annotated with {@link CommandMethod} (or the old {@link CommandMethode}).
     */
    @SuppressWarnings("deprecation")
    private static String commandPattern(java.lang.reflect.Method method) {
        var command = method.getAnnotation(CommandMethod.class);
        if (command != null) {
            return command.value();
        }
        var old = method.getAnnotation(CommandMethode.class);
        return old == null ? null : old.value();
    }

    public void addCommand(@NonNull Object commandInstance) {
        var methods = commandInstance.getClass().getDeclaredMethods();
        Arrays.stream(methods).forEach(methode -> {
            var pattern = commandPattern(methode);
            if (pattern != null) {
                methode.setAccessible(true);
                var description = methode.getAnnotation(CommandDescription.class);
                commands.add(new CommandMethodPair(this, pattern, methode, commandInstance, description));
                return;
            }
            var suggestion = methode.getAnnotation(Suggestion.class);
            if (suggestion != null) {
                methode.setAccessible(true);
                suggestions.add(new SuggestionMethodePair(suggestion, methode, commandInstance));
                return;
            }
            var commandPreProcessor = methode.getAnnotation(CommandPreProcessor.class);
            if (commandPreProcessor != null) {
                methode.setAccessible(true);
                commandPreprocessors.add(new AbstractMap.SimpleEntry<>(methode, commandInstance));
            }
            var parser = methode.getAnnotation(ObjectParser.class);
            if (parser != null) {
                methode.setAccessible(true);
                if (!objectParsers.containsKey(parser.value())) {
                    objectParsers.put(parser.value(), new ArrayList<>());
                }
                objectParsers.get(parser.value()).add(new AbstractMap.SimpleEntry<>(methode, commandInstance));
            }
        });
    }

    public List<String> getFieldSuggestions(String suggestion) {
        var back = new ArrayList<String>();
        suggestions.stream()
                .filter(suggestionMethodePair -> suggestionMethodePair.getSuggestion().value().equals(suggestion))
                .map(SuggestionMethodePair::getCompletion).forEach(back::addAll);
        return back;
    }

    public List<CommandMethodPair> getCommandsWithInput(String input) {
        var command = input.split(" ");
        int index = command.length - 1;
        if (index != -1) {
            return commands.stream()
                    .filter(pair -> pair.getCommandList().length > index)
                    .filter(pair -> pair.getCommandList()[index].startsWith(command[index])
                            || pair.getCommandList()[index].contains("<"))
                    .filter(pair -> pair.canBeSuggestion(command))
                    .sorted(CommandMethodPair::getSorter).toList();
        }
        return commands.stream()
                .filter(pair -> pair.getCommandList().length > 0)
                .filter(pair -> pair.getCommandList()[0].startsWith(input) || pair.getCommandList()[0].contains("<"))
                .sorted(CommandMethodPair::getSorter).toList();
    }

    public void executeCommand(String input) {
        for (Map.Entry<Method, Object> commandPreprocessor : commandPreprocessors) {
            try {
                input = commandPreprocessor.getKey().invoke(commandPreprocessor.getValue(), input).toString();
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        var command = input.split(" ");
        commands.stream().filter(pair -> pair.canBeCommand(command)).min(CommandMethodPair::getSorter)
                .ifPresentOrElse(pair -> {
                    try {
                        pair.getMethod().invoke(pair.getCommandInstance(), pair.parsArguments(command));
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }, () -> Console.error("This command does not exist!"));
    }

}
