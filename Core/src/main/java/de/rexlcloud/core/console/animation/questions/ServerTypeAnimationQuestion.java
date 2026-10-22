package de.rexlcloud.core.console.animation.questions;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;
import de.rexlcloud.core.server.ServerType;

import java.util.Locale;

/**
 * {@code SERVER} or {@code PROXY} (any case).
 */
public class ServerTypeAnimationQuestion extends AnimationQuestion<ServerType> {

    public ServerTypeAnimationQuestion(String key, String question) {
        super(key, question, new AnimationCompletion(ServerType.PROXY.name()),
                new AnimationCompletion(ServerType.SERVER.name()));
    }

    @Override
    public boolean canBeAnswered(String input) {
        try {
            ServerType.valueOf(input.toUpperCase(Locale.ROOT));
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public ServerType parseAnswer(String input) {
        return ServerType.valueOf(input.toUpperCase(Locale.ROOT));
    }

}
