package de.rexlcloud.core.console.animation.questions;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;

/**
 * A decimal number as {@code float}.
 */
public class FloatAnimationQuestion extends AnimationQuestion<Float> {

    public FloatAnimationQuestion(String key, String question, AnimationCompletion... completions) {
        super(key, question, completions);
    }

    @Override
    public boolean canBeAnswered(String input) {
        try {
            Float.parseFloat(input);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public Float parseAnswer(String input) {
        return Float.parseFloat(input);
    }

}
