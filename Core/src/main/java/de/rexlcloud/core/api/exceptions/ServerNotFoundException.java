package de.rexlcloud.core.api.exceptions;

import de.rexlcloud.core.api.Server;
import lombok.Getter;
import lombok.NonNull;

/**
 * There is no server with this name.
 */
public class ServerNotFoundException extends CloudException {

    @Getter
    private final String serverName;

    public ServerNotFoundException(String serverName) {
        super("Cant find Server " + serverName);
        this.serverName = serverName;
    }

    public ServerNotFoundException(@NonNull Server server) {
        super("Cant find Server " + server.getName());
        this.serverName = server.getName();
    }

}
