package de.rexlcloud.core.api.events;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Calls the listeners of the cloud's events; a module gets it with
 * {@link de.rexlcloud.core.api.module.CloudModule#getEventManager()}.
 */
public class EventManager {

    private final HashMap<Class<? extends Event>, ArrayList<EventRegistry>> registeredEvents;

    /**
     * Internal: created by the master.
     */
    public EventManager() {
        registeredEvents = new HashMap<>();
    }

    /**
     * Registers every method of the object that is annotated with {@link EventHandler} and takes one event.
     *
     * @param eventListener the listener
     */
    public void registerEvent(Object eventListener) {
        for (var method : eventListener.getClass().getDeclaredMethods()) {
            var annotation = method.getAnnotation(EventHandler.class);
            if (annotation != null) {
                var parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1) {
                    var clazz = parameterTypes[0];
                    if (getTotalSuperClass(clazz).equals(Event.class)) {
                        var eventClass = (Class<? extends Event>) clazz;
                        if (!registeredEvents.containsKey(eventClass)) {
                            registeredEvents.put(eventClass, new ArrayList<>());
                        }
                        registeredEvents.get(eventClass).add(new EventRegistry(eventListener, method, annotation.priority()));
                    }
                }
            }
        }
    }

    private Class<?> getTotalSuperClass(Class<?> clazz) {
        var lastClass = clazz;
        for (var superclass = clazz.getSuperclass(); superclass != null && superclass != Object.class; superclass = superclass.getSuperclass()) {
            lastClass = superclass;
        }
        return lastClass;
    }

    /**
     * Calls the listeners of the event's exact class, ordered by priority.
     *
     * @param event the event
     */
    public void callEvent(Event event) {
        var eventClass = (Class<? extends Event>) event.getClass();
        if (registeredEvents.containsKey(eventClass)) {
            registeredEvents.get(eventClass).stream()
                    .sorted(Comparator.comparingInt(EventRegistry::getPriority))
                    .forEach(eventRegistry -> {
                        try {
                            eventRegistry.run(event);
                        } catch (InvocationTargetException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

}
