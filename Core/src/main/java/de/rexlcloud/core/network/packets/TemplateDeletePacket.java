package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Master to wrapper: removes files the master's template no longer has, and the templates of deleted groups.
 */
@Getter
@AllArgsConstructor
public class TemplateDeletePacket implements Packet {

    /**
     * Template the {@link #paths} belong to, or {@code null} if only {@link #keepTemplates} is used.
     */
    @Nullable
    private String template;
    private List<String> paths;
    /**
     * If not {@code null}, every template not in this set is deleted (groups that no longer exist).
     */
    @Nullable
    private Set<String> keepTemplates;

}
