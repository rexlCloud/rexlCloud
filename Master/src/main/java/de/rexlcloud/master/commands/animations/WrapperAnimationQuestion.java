package de.rexlcloud.master.commands.animations;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;
import de.rexlcloud.master.RexlCloudMaster;

public class WrapperAnimationQuestion extends AnimationQuestion<String> {

    public WrapperAnimationQuestion(String key, String question) {
        super(key, question, RexlCloudMaster.getInstance().getWrapperManager().getWrapperRegistry().values().stream()
                .map(t -> new AnimationCompletion(t.getName())).toArray(AnimationCompletion[]::new));
    }

    @Override
    public boolean canBeAnswered(String input) {
        return RexlCloudMaster.getInstance().getWrapperManager().getWrapperRegistry().values().stream()
                .anyMatch(t -> t.getName().equals(input));
    }

    @Override
    public String parseAnswer(String input) {
        return RexlCloudMaster.getInstance().getWrapperManager().getWrapperRegistry().values().stream()
                .filter(t -> t.getName().equals(input)).findAny().get().getName();
    }

}
