package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.core.server.template.TemplateFile;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * Wrapper to master: the templates the wrapper has, by template name and relative path.
 */
@Getter
@AllArgsConstructor
public class TemplateStatePacket implements Packet {

    private Map<String, Map<String, TemplateFile>> templates;

}
