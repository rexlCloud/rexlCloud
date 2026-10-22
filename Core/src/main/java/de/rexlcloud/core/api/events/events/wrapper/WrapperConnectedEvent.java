package de.rexlcloud.core.api.events.events.wrapper;

import de.rexlcloud.core.api.Wrapper;
import lombok.NonNull;

/**
 * A wrapper connected to the master.
 */
public class WrapperConnectedEvent extends WrapperEvent {

    public WrapperConnectedEvent(@NonNull Wrapper wrapper) {
        super(wrapper);
    }

}
