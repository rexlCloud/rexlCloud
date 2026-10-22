package de.rexlcloud.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <pre>
 *{@code
 *@CommandPreProcessor
 *public String processedCommand(String input) {
 *    return "processed command";
 *}}</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandPreProcessor {
}
