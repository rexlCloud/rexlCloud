package de.rexlcloud.core.api.exceptions;

import lombok.Getter;

/**
 * There is no wrapper with this name.
 */
public class WrapperNotFoundException extends CloudException {

    @Getter
    private final String wrapperName;

    public WrapperNotFoundException(String wrapperName) {
        super("Cant find Wrapper " + wrapperName);
        this.wrapperName = wrapperName;
    }
}
