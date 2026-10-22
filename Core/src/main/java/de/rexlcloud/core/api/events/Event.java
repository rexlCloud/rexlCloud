package de.rexlcloud.core.api.events;

/**
 * Base of the cloud's events. They are fired on the master; modules listen to them through
 * {@link EventManager#registerEvent(Object)}. Plugins on servers use their platform's events instead.
 */
public abstract class Event {
}
