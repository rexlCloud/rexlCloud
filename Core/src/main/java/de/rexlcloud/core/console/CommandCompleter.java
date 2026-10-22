package de.rexlcloud.core.console;

import de.rexlcloud.core.command.Argument;
import de.rexlcloud.core.command.CommandDescription;
import de.rexlcloud.core.command.CommandMethodPair;
import de.rexlcloud.core.command.CommandRegistry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.jline.utils.AttributedString;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Getter
@AllArgsConstructor
public class CommandCompleter implements Completer {

    CommandRegistry commandRegistry;
    Console console;

    @Override
    public void complete(LineReader reader, final ParsedLine commandLine, final List<Candidate> candidates) {
        var line = commandLine.line();
        var askCompletions = console.getAskCompletions();
        if (askCompletions != null) {
            askCompletions.stream()
                    .filter(t -> line.isBlank() || t.getCompletion().toLowerCase().startsWith(line.toLowerCase()))
                    .forEach(t -> addSuggestion(candidates, t.getCompletion(), t.getDescription()));
        } else if (console.getConsoleAnimations().isEmpty()) {
            var length = line.isBlank() ? 0 : line.split(" ").length - 1;
            var commandsForCompletion = commandRegistry.getCommandsWithInput(line);
            for (var commandMethodePair : commandsForCompletion) {
                if (line.endsWith(" ") && commandMethodePair.getCommandList().length > length) {
                    if (commandMethodePair.getCommandList().length <= length + 1) {
                        break;
                    }
                    var s = commandMethodePair.getCommandList()[length + 1];
                    if (!s.contains("<") && !s.contains(">")) {
                        addSuggestion(candidates, s, commandMethodePair.getDescription());
                    } else {
                        addSuggestionsForParameters(candidates, commandMethodePair, s);
                    }
                } else {
                    if (commandMethodePair.getCommandList().length < length) {
                        return;
                    }
                    var comp = commandMethodePair.getCommandList()[length];
                    if (!comp.contains("<") && !comp.contains(">")) {
                        if (!comp.equalsIgnoreCase(line.trim())) {
                            addSuggestion(candidates, comp, commandMethodePair.getDescription());
                        }
                    } else {
                        addSuggestionsForParameters(candidates, commandMethodePair, comp);
                    }
                }
            }
        } else {
            var question = console.getConsoleAnimations().peek().peekNextQuestion();
            if (question != null && question.getCompletions() != null) {
                Arrays.stream(question.getCompletions()).filter(Objects::nonNull)
                        .filter(t -> line.isBlank() || t.getCompletion().toLowerCase().startsWith(line))
                        .forEach(t -> {
                            addSuggestion(candidates, t.getCompletion(), t.getDescription());
                        });
            }
        }

    }

    private void addSuggestion(List<Candidate> candidates, @NonNull String comp, @Nullable String desc) {
        candidates.add(new Candidate(AttributedString.stripAnsi(comp), comp, null, desc, null, null, true));
    }

    private void addSuggestion(List<Candidate> candidates, @NonNull String comp, @Nullable CommandDescription desc) {
        candidates.add(new Candidate(AttributedString.stripAnsi(comp), comp, null, desc == null ? null : desc.value(),
                null, null, true));
    }

    private void addSuggestionsForParameters(List<Candidate> candidates, CommandMethodPair commandMethodePair,
            String comp) {
        commandMethodePair.getArgumentOrder().keySet().stream()
                .filter(argument -> argument.value().equals(comp.replace("<", "").replace(">", "")))
                .map(Argument::suggestions)
                .filter(sug -> !sug.isBlank()).toList().forEach(sug -> getCommandRegistry().getFieldSuggestions(sug)
                        .forEach(s1 -> addSuggestion(candidates, s1, commandMethodePair.getDescription())));
    }

}
