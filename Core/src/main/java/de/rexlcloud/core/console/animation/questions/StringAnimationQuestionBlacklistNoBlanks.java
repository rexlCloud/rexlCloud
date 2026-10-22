package de.rexlcloud.core.console.animation.questions;

import java.util.List;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;

/**
 * One word (no spaces) except the listed ones, e.g. a new name.
 */
public class StringAnimationQuestionBlacklistNoBlanks extends AnimationQuestion<String> {

    private final List<String> blackList;

    public StringAnimationQuestionBlacklistNoBlanks(String key, String question, AnimationCompletion[] completions,
            String... blackList) {
        super(key, question, completions);
        this.blackList = List.of(blackList);
    }

    public StringAnimationQuestionBlacklistNoBlanks(String key, String question, AnimationCompletion[] completions,
            List<String> blackList) {
        super(key, question, completions);
        this.blackList = blackList;
    }

    @Override
    public boolean canBeAnswered(String input) {
        return !blackList.contains(input) && !input.contains(" ");
    }

    @Override
    public String parseAnswer(String input) {
        return input;
    }

}
