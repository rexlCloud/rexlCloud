package de.rexlcloud.core.api.exceptions;

/**
 * A message channel with this name is registered already.
 */
public class MessageChannelAlreadyRegisteredException extends CloudException {

    public MessageChannelAlreadyRegisteredException() {
    }

    public MessageChannelAlreadyRegisteredException(String message) {
        super(message);
    }
}
