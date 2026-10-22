package de.rexlcloud.core.console.animation.questions;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;

/**
 * A whole number, optionally limited with {@link #setMinValue(int)} and {@link #setMaxValue(int)}.
 */
public class IntegerAnimationQuestion extends AnimationQuestion<Integer> {

    private int minValue, maxValue;
    private boolean minVal, maxVal;

    public IntegerAnimationQuestion(String key, String question, AnimationCompletion... completions) {
        super(key, question, completions);
    }

    /**
     * @return this question, for chaining
     */
    public IntegerAnimationQuestion setMinValue(int minValue) {
        this.minValue = minValue;
        minVal = true;
        return this;
    }

    /**
     * @return this question, for chaining
     */
    public IntegerAnimationQuestion setMaxValue(int maxValue) {
        this.maxValue = maxValue;
        maxVal = true;
        return this;
    }

    @Override
    public boolean canBeAnswered(String input) {
        try {
            var pars = Integer.parseInt(input);
            return (!minVal || pars >= minValue) && (!maxVal || pars <= maxValue);
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public Integer parseAnswer(String input) {
        return Integer.parseInt(input);
    }

}
