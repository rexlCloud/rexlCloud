package de.rexlcloud.core.command;

import lombok.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 *{@code
 *@ObjectParser
 *public Object parsObject(String input) {
 *    return object;
 *}}</pre>
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ObjectParser {

    /**
     * The parameter type this method converts arguments to.
     */
    @NonNull Class<?> value();

}
