package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Wrapper to master: one chunk of a zip with a server's files, in order. The last chunk carries the SHA-256 of the
 * whole zip; a failed save sends a single packet with {@link #error}.
 */
@Getter
@AllArgsConstructor
public class TemplateUploadPacket implements Packet {

    private UUID requestId;
    private int chunk;
    private boolean last;
    private byte[] data;
    private String sha256;
    private String error;

}
