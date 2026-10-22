package de.rexlcloud.core.command;

import lombok.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *Default implementation:
 *
 *<pre>{@code
 *      @Suggestion("name")
 *      public List<String> methode() {
 *      }}</pre>
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Suggestion {

    /**
     * Name the arguments refer to with {@link Argument#suggestions()}.
     */
    @NonNull String value();

}
