package de.rexlcloud.core.api.events.events.wrapper;

import de.rexlcloud.core.api.Wrapper;
import lombok.NonNull;

/**
 * A wrapper's connection to the master ended.
 */
public class WrapperDisconnectedEvent extends WrapperEvent {

    public WrapperDisconnectedEvent(@NonNull Wrapper wrapper) {
        super(wrapper);
    }

}
