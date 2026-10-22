package de.rexlcloud.core.command;

import lombok.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a console command; the value is the command with its arguments, e.g. {@code "group delete <name>"}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandMethod {

    /**
     * The command: fixed words and {@code <arguments>}, e.g. {@code "group delete <name>"}.
     */
    @NonNull String value();

}
