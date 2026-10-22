package de.rexlcloud.core.console.animation.questions;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;

/**
 * A whole number that may exceed the range of {@code int}.
 */
public class LongAnimationQuestion extends AnimationQuestion<Long> {

    public LongAnimationQuestion(String key, String question, AnimationCompletion... completions) {
        super(key, question, completions);
    }

    @Override
    public boolean canBeAnswered(String input) {
        try {
            Long.parseLong(input);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public Long parseAnswer(String input) {
        return Long.parseLong(input);
    }

}
