package de.rexlcloud.core.console;

import lombok.AllArgsConstructor;

import java.util.Locale;

@AllArgsConstructor
public enum Color {

    RESET("\u001B[0m", "\u001B[0m"),
    ERROR("\033[38;2;181;60;60m", "\033[38;5;9m"),
    WARN(fromHex("#ffff33"), "\033[38;2;11m"),
    MAIN("\033[38;2;227;50;186m", "\033[38;5;15m"),
    INFO(fromHex("#99ff99"), "\033[38;5;82m"),
    DEBUG(fromHex("#99ff99"), "\033[38;5;82m"),
    HIGHLIGHT("\033[38;2;50;203;227m", "\033[38;5;10m");
    private final String ansiCode;
    private final String ansiCode8Bit;

    private static final boolean use8Bit = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).contains("nux");

    public static String fromRGB(int r, int g, int b) {
        return "\033[38;2;" + r + ";" + g + ";" + b + "m";
    }

    public static String fromHex(String hex) {
        hex = hex.replace("#", "");
        var r = Integer.valueOf(hex.substring(0, 2), 16);
        var g = Integer.valueOf(hex.substring(2, 4), 16);
        var b = Integer.valueOf(hex.substring(4, 6), 16);
        return fromRGB(r, g, b);
    }

    @Override
    public String toString() {
        return use8Bit ? ansiCode8Bit : ansiCode;
    }
}
