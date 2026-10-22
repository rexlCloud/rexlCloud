package de.rexlcloud.core.api.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Internal: a registered listener method.
 */
@Getter
@AllArgsConstructor
public class EventRegistry {

    private Object object;
    private Method method;
    private int priority;

    protected void run(Event event) throws InvocationTargetException, IllegalAccessException {
        method.setAccessible(true);
        method.invoke(object, event);
    }

}
