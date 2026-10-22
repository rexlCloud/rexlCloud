package de.rexlcloud.core.console.animation.questions;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;

/**
 * A decimal number.
 */
public class DoubleAnimationQuestion extends AnimationQuestion<Double> {

    public DoubleAnimationQuestion(String key, String question, AnimationCompletion... completions) {
        super(key, question, completions);
    }

    @Override
    public boolean canBeAnswered(String input) {
        try {
            Double.parseDouble(input);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public Double parseAnswer(String input) {
        return Double.parseDouble(input);
    }

}
