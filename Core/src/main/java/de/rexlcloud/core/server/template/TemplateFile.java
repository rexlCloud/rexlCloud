package de.rexlcloud.core.server.template;

import java.io.Serial;
import java.io.Serializable;

/**
 * Size and SHA-256 of a template file, used to send only what changed.
 */
public record TemplateFile(long size, String sha256) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

}
