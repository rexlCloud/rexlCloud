package de.rexlcloud.core.api.module;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The contents of a module's {@code module.json}.
 */
@Getter
@AllArgsConstructor
public class ModuleInfo {

    /**
     * Name of the module, also the name of its data folder.
     */
    private String name;
    /**
     * The class that extends {@link CloudModule}.
     */
    private String mainClass;
    /**
     * Version of the module.
     */
    private String version;
    /**
     * Author of the module.
     */
    private String author;

}
