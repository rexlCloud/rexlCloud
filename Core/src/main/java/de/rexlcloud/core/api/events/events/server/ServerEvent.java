package de.rexlcloud.core.api.events.events.server;

import de.rexlcloud.core.api.Server;
import de.rexlcloud.core.api.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * An event about a server or proxy.
 */
@Getter
@AllArgsConstructor
public abstract class ServerEvent extends Event {

    /**
     * The server.
     */
    @NonNull
    private final Server server;

}
