package de.rexlcloud.core.api.exceptions;

/**
 * Base of the exceptions of the rexlCloud API. They can be sent between cloud components and keep their type and
 * message.
 */
public class CloudException extends RuntimeException {

    /**
     * Message of an exception received from another component, which its constructor might have changed.
     */
    private String receivedMessage;

    public CloudException(String message) {
        super(message);
    }

    public CloudException() {
        super();
    }

    @Override
    public String getMessage() {
        return receivedMessage != null ? receivedMessage : super.getMessage();
    }

    /**
     * Internal: restores the message of an exception received from another component.
     */
    public void restoreMessage(String message) {
        this.receivedMessage = message;
    }

}
