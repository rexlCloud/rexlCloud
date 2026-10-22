package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Master to wrapper: send the files of this run of a server as a template variant (see {@link TemplateUploadPacket}).
 */
@Getter
@AllArgsConstructor
public class ServerSaveTemplatePacket implements Packet {

    private String name;
    private UUID key;
    private String variant;
    /**
     * Identifies the upload; the master only accepts uploads it asked for.
     */
    private UUID requestId;

}
