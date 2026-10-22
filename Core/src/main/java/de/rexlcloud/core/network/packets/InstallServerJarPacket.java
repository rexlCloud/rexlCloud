package de.rexlcloud.core.network.packets;

import de.rexlcloud.core.network.packets.types.Packet;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.files.ServerJar;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InstallServerJarPacket implements Packet {

    private ServerGroup serverGroup;
    private ServerJar serverJar;

}
