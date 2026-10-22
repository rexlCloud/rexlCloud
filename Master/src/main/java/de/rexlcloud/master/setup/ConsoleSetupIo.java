package de.rexlcloud.master.setup;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.console.animation.AnimationCompletion;

import java.util.List;

/**
 * The setup's questions on the master console, with Tab completion of the choices.
 */
public class ConsoleSetupIo implements FirstStartSetup.Io {

    @Override
    public String ask(String question, String defaultAnswer, boolean hidden, List<String> choices) {
        return Console.ask(question, defaultAnswer, hidden,
                choices.stream().map(AnimationCompletion::new).toArray(AnimationCompletion[]::new));
    }

    @Override
    public void say(String line) {
        Console.info(line);
    }

}
