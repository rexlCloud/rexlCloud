package de.rexlcloud.core.console.animation.questions;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;

import java.util.Arrays;
import java.util.List;

/**
 * One of the listed answers, which Tab offers.
 */
public class StringAnimationQuestionWhitelist extends AnimationQuestion<String> {

    private final List<String> whiteList;

    public StringAnimationQuestionWhitelist(String key, String question, String... whiteList) {
        super(key, question, Arrays.stream(whiteList).map(AnimationCompletion::new).toArray(AnimationCompletion[]::new));
        this.whiteList = List.of(whiteList);
    }

    public StringAnimationQuestionWhitelist(String key, String question, List<String> whiteList) {
        super(key, question, whiteList.stream().map(AnimationCompletion::new).toArray(AnimationCompletion[]::new));
        this.whiteList = whiteList;
    }

    @Override
    public boolean canBeAnswered(String input) {
        return whiteList.contains(input);
    }

    @Override
    public String parseAnswer(String input) {
        return input;
    }

}
