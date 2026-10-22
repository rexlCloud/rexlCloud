package de.rexlcloud.core.command;

import lombok.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated misspelled, use {@link CommandMethod}; still works for existing modules
 */
@Deprecated
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandMethode {

    @NonNull String value();

}
