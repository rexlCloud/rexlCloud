package de.rexlcloud.core.console.animation.questions;

import java.util.List;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;

/**
 * Any text except the listed ones, e.g. names that are taken.
 */
public class StringAnimationQuestionBlacklist extends AnimationQuestion<String> {

    private final List<String> blackList;

    public StringAnimationQuestionBlacklist(String key, String question, AnimationCompletion[] completions,
            String... blackList) {
        super(key, question, completions);
        this.blackList = List.of(blackList);
    }

    public StringAnimationQuestionBlacklist(String key, String question, AnimationCompletion[] completions,
            List<String> blackList) {
        super(key, question, completions);
        this.blackList = blackList;
    }

    @Override
    public boolean canBeAnswered(String input) {
        return !blackList.contains(input);
    }

    @Override
    public String parseAnswer(String input) {
        return input;
    }

}
