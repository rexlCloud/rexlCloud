package de.rexlcloud.core.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.Map;

/**
 * Text that admins write (messages, MOTD): MiniMessage, plus the classic {@code &} and {@code §} colour codes.
 */
public final class CloudText {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<Character, String> COLORS = Map.ofEntries(
            Map.entry('0', "#000000"), Map.entry('1', "#0000aa"), Map.entry('2', "#00aa00"), Map.entry('3', "#00aaaa"),
            Map.entry('4', "#aa0000"), Map.entry('5', "#aa00aa"), Map.entry('6', "#ffaa00"), Map.entry('7', "#aaaaaa"),
            Map.entry('8', "#555555"), Map.entry('9', "#5555ff"), Map.entry('a', "#55ff55"), Map.entry('b', "#55ffff"),
            Map.entry('c', "#ff5555"), Map.entry('d', "#ff55ff"), Map.entry('e', "#ffff55"), Map.entry('f', "#ffffff"));

    private CloudText() {
    }

    public static Component parse(String text) {
        return MINI_MESSAGE.deserialize(replaceColorCodes(text == null ? "" : text));
    }

    /**
     * The text as JSON chat component, the format messages are sent to players in.
     */
    public static String toJson(String text) {
        return GsonComponentSerializer.gson().serialize(parse(text));
    }

    /**
     * {@code &a} / {@code §a} to MiniMessage colours.
     */
    public static String replaceColorCodes(String text) {
        var result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                var color = COLORS.get(Character.toLowerCase(text.charAt(i + 1)));
                if (color != null) {
                    result.append('<').append(color).append('>');
                    i++;
                    continue;
                }
            }
            result.append(c);
        }
        return result.toString();
    }

}
