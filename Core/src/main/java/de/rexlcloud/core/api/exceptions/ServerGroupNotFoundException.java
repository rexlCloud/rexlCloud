package de.rexlcloud.core.api.exceptions;

import lombok.Getter;

/**
 * There is no group with this name.
 */
public class ServerGroupNotFoundException extends CloudException {

    @Getter
    private final String serverGroupName;

    public ServerGroupNotFoundException(String serverGroupName) {
        super("Cant find ServerGroup " + serverGroupName);
        this.serverGroupName = serverGroupName;
    }
}
