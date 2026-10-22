package de.rexlcloud.core.console.animation;

import de.rexlcloud.core.server.ServerType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * A series of questions asked in the master console one after another, like {@code group create} does. Add
 * questions, register what happens with the answers in {@link #onFinish(Consumer)}, then start it with
 * {@link de.rexlcloud.core.console.Console#addConsoleAnimation(ConsoleAnimation)}.
 * <p>
 * While it runs, the console shows only the current question; Tab offers the question's completions, an answer the
 * question does not accept asks it again, and Ctrl+C cancels ({@link #onCancel(Consumer)}). Afterwards the log lines
 * written meanwhile are shown again. The callbacks run on the console thread: start a thread for anything slow.
 * <pre>{@code
 * var animation = new ConsoleAnimation()
 *         .addQuestion(new StringAnimationQuestion("name", "Name of the arena?"))
 *         .addQuestion(new IntegerAnimationQuestion("players", "Players per team?").setMinValue(1));
 * animation.onFinish(done -> createArena(done.getDirectString("name"), done.getDirectInt("players")));
 * Console.addConsoleAnimation(animation);
 * }</pre>
 */
public class ConsoleAnimation {

    private final Queue<AnimationQuestion<?>> queue;
    private final List<Consumer<ConsoleAnimation>> onFinish;
    private final List<Consumer<ConsoleAnimation>> onCancel;
    private final ArrayList<AnimationQuestion<?>> finishList;

    public ConsoleAnimation() {
        queue = new LinkedList<>();
        onFinish = new ArrayList<>();
        onCancel = new ArrayList<>();
        finishList = new ArrayList<>();
    }

    /**
     * Adds a question after the ones already added. Questions added while the animation runs (e.g. from
     * {@link AnimationQuestion#onFinishFirst(Consumer)}) are asked after all others.
     *
     * @return this animation, for chaining
     */
    public ConsoleAnimation addQuestion(AnimationQuestion<?> animationQuestion) {
        queue.add(animationQuestion);
        return this;
    }

    /**
     * Internal: the question to ask now, used by the console.
     */
    public AnimationQuestion<?> nextQuestion() {
        var next = queue.peek();
        if (next != null) {
            next.onFinish(e -> {
                if (queue.remove(e)) {
                    finishList.add(e);
                    if (queue.isEmpty()) {
                        finish();
                    }
                }
            });
        }
        return next;
    }

    /**
     * @return the question asked now, {@code null} when all are answered
     */
    @Nullable
    public AnimationQuestion<?> peekNextQuestion() {
        return queue.peek();
    }

    /**
     * @return whether every question is answered
     */
    public boolean isFinish() {
        return queue.isEmpty();
    }

    /**
     * Internal: calls the {@link #onFinish(Consumer)} callbacks once the last question is answered.
     */
    public void finish() {
        onFinish.forEach(t -> t.accept(this));
    }

    /**
     * Internal: calls the {@link #onCancel(Consumer)} callbacks, e.g. on Ctrl+C.
     */
    public void cancel() {
        onCancel.forEach(t -> t.accept(this));
    }

    /**
     * Called on the console thread once every question is answered; read the answers with {@link #get(String)} or the
     * {@code getDirect...} methods.
     */
    public void onFinish(Consumer<ConsoleAnimation> consumer) {
        this.onFinish.add(consumer);
    }

    /**
     * Called when the user cancels with Ctrl+C; answers given so far are dropped.
     */
    public void onCancel(Consumer<ConsoleAnimation> consumer) {
        this.onCancel.add(consumer);
    }

    /**
     * @param key the key the question was created with
     * @return the parsed answer, empty if that question was not asked
     */
    public <T> Optional<T> get(String key) {
        return finishList.stream()
                .filter(t -> t.getKey().equals(key))
                .map(t -> (T) t.get()).findAny();
    }

    /**
     * @return the parsed answer of the question with this key, {@code null} if it was not asked
     */
    @Nullable
    public <T> T getDirect(String key) {
        return finishList.stream()
                .filter(t -> t.getKey().equals(key))
                .map(t -> (T) t.get()).findAny().orElse(null);
    }

    /**
     * @return the answer of a {@link de.rexlcloud.core.console.animation.questions.BooleanAnimationQuestion}
     */
    @Nullable
    public Boolean getDirectBoolean(String key) {
        return finishList.stream()
                .filter(t -> t.getKey().equals(key))
                .map(t -> (Boolean) t.get()).findAny().orElse(null);
    }

    /**
     * @return the answer of a string question
     */
    @Nullable
    public String getDirectString(String key) {
        return finishList.stream()
                .filter(t -> t.getKey().equals(key))
                .map(t -> (String) t.get()).findAny().orElse(null);
    }

    /**
     * @return the answer of an {@link de.rexlcloud.core.console.animation.questions.IntegerAnimationQuestion}
     */
    @Nullable
    public Integer getDirectInt(String key) {
        return finishList.stream()
                .filter(t -> t.getKey().equals(key))
                .map(t -> (Integer) t.get()).findAny().orElse(null);
    }

    /**
     * @return the answer of a {@link de.rexlcloud.core.console.animation.questions.LongAnimationQuestion}
     */
    @Nullable
    public Long getDirectLong(String key) {
        return finishList.stream()
                .filter(t -> t.getKey().equals(key))
                .map(t -> (Long) t.get()).findAny().orElse(null);
    }

    /**
     * @return the answer of a question that parses to a {@code Short}
     */
    @Nullable
    public Short getDirectShort(String key) {
        return finishList.stream()
                .filter(t -> t.getKey().equals(key))
                .map(t -> (Short) t.get()).findAny().orElse(null);
    }

    /**
     * @return the answer of a question that parses to a {@code Byte}
     */
    @Nullable
    public Byte getDirectByte(String key) {
        return finishList.stream()
                .filter(t -> t.getKey().equals(key))
                .map(t -> (Byte) t.get()).findAny().orElse(null);
    }

    /**
     * @return the answer of a {@link de.rexlcloud.core.console.animation.questions.ServerTypeAnimationQuestion}
     */
    @Nullable
    public ServerType getDirectServerType(String key) {
        return finishList.stream()
                .filter(t -> t.getKey().equals(key))
                .map(t -> (ServerType) t.get()).findAny().orElse(null);
    }

}
