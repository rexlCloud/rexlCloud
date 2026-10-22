package de.rexlcloud.core.command;

import de.rexlcloud.core.server.ServerType;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;

@Getter
public class CommandMethodPair {

    private final CommandRegistry commandRegistry;
    private final Object commandInstance;
    /**
     * The command with its arguments, e.g. {@code "group delete <name>"}.
     */
    private final String pattern;
    private final Method method;
    private final String[] commandList;
    private final LinkedHashMap<Argument, Class<?>> argumentOrder;
    @Nullable
    private final CommandDescription description;

    public CommandMethodPair(CommandRegistry commandRegistry, String pattern, Method method, Object commandInstance) {
        this(commandRegistry, pattern, method, commandInstance, null);
    }

    public CommandMethodPair(CommandRegistry commandRegistry, String pattern, Method method, Object commandInstance, @Nullable CommandDescription description) {
        this.commandRegistry = commandRegistry;
        this.pattern = pattern;
        this.method = method;
        this.commandInstance = commandInstance;
        this.description = description;
        commandList = pattern.split(" ");
        argumentOrder = new LinkedHashMap<>();
        for (var parameter : method.getParameters()) {
            var annotation = parameter.getAnnotation(Argument.class);
            if (annotation == null) {
                continue;
            }
            argumentOrder.put(annotation, parameter.getType());
        }
    }

    public boolean canBeCommand(String[] args) {
        if (args.length > commandList.length) {
            if (argumentOrder.keySet().stream().anyMatch(Argument::greedy)) {
                return true;
            }
        }
        for (int i = 0; i < args.length; i++) {
            if (!args[i].equals(commandList[i]) && !commandList[i].contains("<")) {
                return false;
            }
        }
        return true;
    }

    public boolean canBeSuggestion(String[] args) {
        if (args.length - 1 > commandList.length) {
            return false;
        }
        for (int i = 0; i < args.length - 1; i++) {
            if (!args[i].equals(commandList[i]) && !commandList[i].contains("<")) {
                return false;
            }
        }
        return true;
    }

    public int getSorter(CommandMethodPair other) {
        return Integer.compare(other.argumentOrder.size(), argumentOrder.size());
    }

    public Object[] parsArguments(String[] input) {
        var list = new ArrayList<>();
        for (var entry : argumentOrder.entrySet()) {
            var argument = entry.getKey();
            if (argument.greedy()) {
                if (!entry.getValue().equals(String.class)) {
                    throw new RuntimeException("Greedy argument is not of type string!");
                }
                var index = getIndexOf(commandList, "<" + argument.value() + ">");
                var stringBuilder = new StringBuilder();
                for (int i = index; i < input.length; i++) {
                    stringBuilder.append(input[i]).append(" ");
                }
                list.add(stringBuilder.toString().trim());
                return list.toArray();
            } else {
                list.add(parsObject(input[getIndexOf(commandList, "<" + argument.value() + ">")], entry.getValue()));
            }
        }
        return list.toArray();
    }

    private Object parsObject(String input, Class<?> clazz) {
        if (clazz.equals(int.class)) {
            return Integer.parseInt(input);
        }
        if (clazz.equals(boolean.class)) {
            return Boolean.parseBoolean(input);
        }
        if (clazz.equals(long.class)) {
            return Long.parseLong(input);
        }
        if (clazz.equals(float.class)) {
            return Float.parseFloat(input);
        }
        if (clazz.equals(double.class)) {
            return Double.parseDouble(input);
        }
        if (clazz.equals(short.class)) {
            return Short.parseShort(input);
        }
        if (clazz.equals(UUID.class)) {
            return UUID.fromString(input);
        }
        if (clazz.equals(ServerType.class)) {
            try {
                return ServerType.valueOf(input.toUpperCase());
            } catch (Exception ignored) {
                return null;
            }
        }
        var objectParsers = commandRegistry.getObjectParsers();
        if (objectParsers.containsKey(clazz)) {
            if (!objectParsers.get(clazz).isEmpty()) {
                // The parser registered last for this type
                var parsers = objectParsers.get(clazz);
                var parserEntry = parsers.get(parsers.size() - 1);
                try {
                    parserEntry.getKey().setAccessible(true);
                    return parserEntry.getKey().invoke(parserEntry.getValue(), input);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }
        return input;
    }

    private int getIndexOf(String[] strings, String item) {
        for (int i = 0; i < strings.length; i++) {
            if (item.equals(strings[i])) return i;
        }
        return -1;
    }

}
