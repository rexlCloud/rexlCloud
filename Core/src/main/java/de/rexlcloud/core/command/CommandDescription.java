package de.rexlcloud.core.command;

import lombok.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A short description of a {@link CommandMethod} command, shown in the console's help.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandDescription {

    /**
     * The description.
     */
    @NonNull String value();

}
