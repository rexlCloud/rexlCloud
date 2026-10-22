package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Master to wrapper: one chunk of a template file. The chunks of a file arrive in order; after the last one the
 * wrapper checks size and SHA-256 and only then replaces the file.
 */
@Getter
@AllArgsConstructor
public class TemplateFilePacket implements Packet {

    private String template;
    private String path;
    private long size;
    private String sha256;
    private int chunk;
    private int chunks;
    /**
     * The chunk, encrypted with the wrapper's key.
     */
    private byte[] data;

}
