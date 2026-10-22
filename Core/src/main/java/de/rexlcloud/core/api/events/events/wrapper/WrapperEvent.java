package de.rexlcloud.core.api.events.events.wrapper;

import de.rexlcloud.core.api.Wrapper;
import de.rexlcloud.core.api.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * An event about a wrapper.
 */
@Getter
@AllArgsConstructor
public abstract class WrapperEvent extends Event {

    /**
     * The wrapper.
     */
    @NonNull
    private final Wrapper wrapper;

}
