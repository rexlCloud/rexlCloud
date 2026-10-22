package de.rexlcloud.core.console.animation;

import de.rexlcloud.core.console.Color;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One question of a {@link ConsoleAnimation}, answered with a value of type {@code T}. The ready-made questions are in
 * {@link de.rexlcloud.core.console.animation.questions}; for other answers extend this class:
 * <pre>{@code
 * public class UuidAnimationQuestion extends AnimationQuestion<UUID> {
 *     public UuidAnimationQuestion(String key, String question) {
 *         super(key, question);
 *     }
 *
 *     public boolean canBeAnswered(String input) {
 *         try {
 *             UUID.fromString(input);
 *             return true;
 *         } catch (IllegalArgumentException e) {
 *             return false;
 *         }
 *     }
 *
 *     public UUID parseAnswer(String input) {
 *         return UUID.fromString(input);
 *     }
 * }
 * }</pre>
 *
 * @param <T> type of the answer
 */
@Getter
public abstract class AnimationQuestion<T> {

    /**
     * Name the answer is read with, e.g. {@link ConsoleAnimation#getDirectString(String)}.
     */
    @NonNull
    private final String key;
    /**
     * The text shown in the console.
     */
    @NonNull
    private final String question;
    /**
     * Answers offered with Tab, {@code null} for none.
     */
    @Nullable
    private final AnimationCompletion[] completions;
    @Getter(value = AccessLevel.PRIVATE)
    private List<Consumer<AnimationQuestion<T>>> consumer;
    private T answer;

    /**
     * @param key         name the answer is read with
     * @param question    the text shown in the console
     * @param completions answers offered with Tab
     */
    public AnimationQuestion(@NonNull String key, @NonNull String question,
            @Nullable AnimationCompletion... completions) {
        this.key = key;
        this.question = question;
        this.completions = completions;
        this.consumer = new ArrayList<>();
    }

    /**
     * Called on the console thread once this question is answered.
     */
    public void onFinish(Consumer<AnimationQuestion<T>> consumer) {
        this.consumer.add(consumer);
    }

    /**
     * Like {@link #onFinish(Consumer)}, but before the callbacks registered so far, e.g. to add follow-up questions to
     * the animation depending on this answer before it checks whether it is finished.
     */
    public void onFinishFirst(Consumer<AnimationQuestion<T>> consumer) {
        this.consumer.add(0, consumer);
    }

    /**
     * @param input the trimmed line the user entered
     * @return whether the input is a valid answer; otherwise the question is asked again
     */
    public abstract boolean canBeAnswered(String input);

    /**
     * @param input an input {@link #canBeAnswered(String)} accepted
     * @return the answer
     */
    public abstract T parseAnswer(String input);

    /**
     * @return the question text in the console's colour
     */
    public final String getQuestion() {
        return Color.INFO + question + Color.RESET;
    }

    /**
     * Internal: stores the answer and calls the {@link #onFinish(Consumer)} callbacks.
     */
    public void finish(String input) {
        answer = parseAnswer(input);
        consumer.forEach(t -> t.accept(this));
    }

    /**
     * @return the parsed answer, {@code null} before the question is answered
     */
    public T get() {
        return answer;
    }

}
