package de.rexlcloud.core.api.events.events.server;

import de.rexlcloud.core.api.Server;
import lombok.NonNull;

/**
 * A server came online (its cloud plugin connected).
 */
public class ServerStartEvent extends ServerEvent {

    public ServerStartEvent(@NonNull Server server) {
        super(server);
    }

}
