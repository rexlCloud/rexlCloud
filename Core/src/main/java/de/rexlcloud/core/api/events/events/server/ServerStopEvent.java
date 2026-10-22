package de.rexlcloud.core.api.events.events.server;

import de.rexlcloud.core.api.Server;
import lombok.NonNull;

/**
 * An online server is stopping.
 */
public class ServerStopEvent extends ServerEvent {

    public ServerStopEvent(@NonNull Server server) {
        super(server);
    }

}
