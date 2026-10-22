package de.rexlcloud.core.console.animation.questions;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;

/**
 * A yes/no question: {@code true} or {@code false}.
 */
public class BooleanAnimationQuestion extends AnimationQuestion<Boolean> {

    public BooleanAnimationQuestion(String key, String question) {
        super(key, question, new AnimationCompletion("true"), new AnimationCompletion("false"));
    }

    @Override
    public boolean canBeAnswered(String input) {
        return input.equalsIgnoreCase("true") || input.equalsIgnoreCase("false");
    }

    @Override
    public Boolean parseAnswer(String input) {
        return Boolean.parseBoolean(input);
    }

}
