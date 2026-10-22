package de.rexlcloud.core.api.exceptions;

/**
 * Too many requests of this server are waiting for an answer; try again later.
 */
public class ToManyRequestsException extends CloudException {

    public ToManyRequestsException() {
    }

    public ToManyRequestsException(String message) {
        super(message);
    }
}
