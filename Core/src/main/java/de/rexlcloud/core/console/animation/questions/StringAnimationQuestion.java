package de.rexlcloud.core.console.animation.questions;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;

/**
 * Any text.
 */
public class StringAnimationQuestion extends AnimationQuestion<String> {

    public StringAnimationQuestion(String key, String question, AnimationCompletion... completions) {
        super(key, question, completions);
    }

    @Override
    public boolean canBeAnswered(String input) {
        return true;
    }

    @Override
    public String parseAnswer(String input) {
        return input;
    }

}
