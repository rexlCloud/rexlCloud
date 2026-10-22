package de.rexlcloud.core.command;

import lombok.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter of a {@link CommandMethod} method as the argument with this name in the command, e.g.
 * {@code <player>}. Parameters of type {@code String}, {@code int}, {@code long}, {@code short}, {@code float},
 * {@code double}, {@code boolean}, {@link java.util.UUID} and {@link de.rexlcloud.core.server.ServerType} are converted
 * automatically, other types with an {@link ObjectParser}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Argument {

    /**
     * Name of the argument in the command, without the angle brackets.
     */
    @NonNull String value();

    /**
     * Name of the {@link Suggestion} that completes this argument in the console.
     */
    @NonNull String suggestions() default "";

    /**
     *
     * <pre>
     *only possible for the last argument in a command.
     *greedy arguments will take all strings after that argument as its argument.</pre>
     */
    boolean greedy() default false;

}
