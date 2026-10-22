package de.rexlcloud.core.api.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a listener (see {@link EventManager#registerEvent(Object)}) that takes exactly one event as
 * parameter.
 * <pre>{@code
 * @EventHandler
 * public void onJoin(PlayerCloudJoinEvent event) {
 *     Console.info("%s joined", event.getPlayer().getName());
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    /**
     * Listeners with a lower priority are called first.
     */
    int priority() default 0;

}
