package de.rexlcloud.core.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandRegistryTest {

    static final class Commands {
        final List<String> calls = new ArrayList<>();

        @CommandMethod("greet <name>")
        void greet(@Argument("name") String name) {
            calls.add("greet " + name);
        }

        @SuppressWarnings("deprecation")
        @CommandMethode("old")
        void old() {
            calls.add("old");
        }
    }

    @Test
    void theNewAndTheOldAnnotationBothRegisterCommands() {
        var registry = new CommandRegistry();
        var commands = new Commands();
        registry.addCommand(commands);

        registry.executeCommand("greet Notch");
        registry.executeCommand("old");

        assertEquals(List.of("greet Notch", "old"), commands.calls);
    }

}
