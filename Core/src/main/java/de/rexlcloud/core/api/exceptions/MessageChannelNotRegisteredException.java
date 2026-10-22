package de.rexlcloud.core.api.exceptions;

/**
 * No message channel with this name is registered.
 */
public class MessageChannelNotRegisteredException extends CloudException {

    public MessageChannelNotRegisteredException() {
    }

    public MessageChannelNotRegisteredException(String message) {
        super(message);
    }
}
