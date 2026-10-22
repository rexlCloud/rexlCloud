package de.rexlcloud.core.console.animation;

import de.rexlcloud.core.console.animation.questions.BooleanAnimationQuestion;
import de.rexlcloud.core.console.animation.questions.DoubleAnimationQuestion;
import de.rexlcloud.core.console.animation.questions.IntegerAnimationQuestion;
import de.rexlcloud.core.console.animation.questions.LongAnimationQuestion;
import de.rexlcloud.core.console.animation.questions.ServerTypeAnimationQuestion;
import de.rexlcloud.core.console.animation.questions.StringAnimationQuestionWhitelist;
import de.rexlcloud.core.server.ServerType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AnimationQuestionsTest {

    @Test
    void numbersThatDoNotParseAreAskedAgain() {
        assertFalse(new IntegerAnimationQuestion("n", "?").canBeAnswered("abc"));
        assertFalse(new LongAnimationQuestion("n", "?").canBeAnswered("1.5"));
        assertFalse(new DoubleAnimationQuestion("n", "?").canBeAnswered("x"));
        assertTrue(new DoubleAnimationQuestion("n", "?").canBeAnswered("1.5"));
    }

    @Test
    void integerLimitsAreChecked() {
        var question = new IntegerAnimationQuestion("n", "?").setMinValue(1).setMaxValue(10);
        assertFalse(question.canBeAnswered("0"));
        assertFalse(question.canBeAnswered("11"));
        assertTrue(question.canBeAnswered("10"));
    }

    @Test
    void theServerTypeIgnoresCase() {
        var question = new ServerTypeAnimationQuestion("type", "?");
        assertTrue(question.canBeAnswered("proxy"));
        assertEquals(ServerType.PROXY, question.parseAnswer("proxy"));
        assertFalse(question.canBeAnswered("lobby"));
    }

    /**
     * What the console does with each line: ask the current question, finish it if the answer fits.
     */
    private static void answer(ConsoleAnimation animation, String line) {
        var question = animation.nextQuestion();
        if (question.canBeAnswered(line)) {
            question.finish(line);
        }
    }

    @Test
    void answersAreReadByKeyAndFollowUpQuestionsAreAsked() {
        var finished = new ArrayList<ConsoleAnimation>();
        var animation = new ConsoleAnimation();
        var teams = new BooleanAnimationQuestion("teams", "Teams?");
        teams.onFinishFirst(answer -> {
            if (answer.get()) {
                animation.addQuestion(new IntegerAnimationQuestion("size", "Team size?").setMinValue(1));
            }
        });
        animation.addQuestion(new StringAnimationQuestionWhitelist("mode", "Mode?", "solo", "duo"))
                .addQuestion(teams);
        animation.onFinish(finished::add);

        answer(animation, "trio");   // not offered: asked again
        answer(animation, "duo");
        answer(animation, "true");
        assertTrue(finished.isEmpty(), "the follow-up question is still open");
        answer(animation, "0");      // below the minimum
        answer(animation, "2");

        assertEquals(1, finished.size());
        assertEquals("duo", animation.getDirectString("mode"));
        assertEquals(true, animation.getDirectBoolean("teams"));
        assertEquals(2, animation.getDirectInt("size"));
        assertTrue(animation.get("missing").isEmpty());
    }

}
