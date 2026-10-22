package de.rexlcloud.core.api.exceptions;

/**
 * The master did not answer the request within 10 seconds.
 */
public class TimeOutException extends CloudException {

    public TimeOutException() {
    }

    public TimeOutException(String message) {
        super(message);
    }
}
