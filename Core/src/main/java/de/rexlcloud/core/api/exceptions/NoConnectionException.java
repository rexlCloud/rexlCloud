package de.rexlcloud.core.api.exceptions;

/**
 * The plugin is not connected to its wrapper (e.g. while the wrapper restarts), so the request was not sent.
 */
public class NoConnectionException extends CloudException {

    public NoConnectionException() {
    }

    public NoConnectionException(String message) {
        super(message);
    }
}
