package de.rexlcloud.core.config;

import de.rexlcloud.core.network.packets.types.Packet;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;

import static de.rexlcloud.core.config.MessageKey.*;

@Getter
@Accessors(fluent = true)
public class PluginMessageConfig implements Packet {

    private final LinkedHashMap<MessageKey, String> messages = new LinkedHashMap<>();

    public void loadDefault() {
        //ServerGroup messages
        messages.putIfAbsent(GROUP_LIST_MESSAGE, "<gray>Group: <yellow><name><newline><gray>Type: <yellow><type><newline><gray>Wrapper: <yellow><wrapper><newline><gray>Memory: <yellow><memory>");
        messages.putIfAbsent(GROUP_INFO_MESSAGE, "<gray>Group: <yellow><name><newline><gray>Type: <yellow><type><newline><gray>Wrapper: <yellow><wrapper><newline><gray>Memory: <yellow><memory><newline><gray>Min servers: <yellow><min_servers><newline><gray>Java arguments: <yellow><java_args>");
        messages.putIfAbsent(GROUP_NOT_FOUND_MESSAGE, "<red>The ServerGroup <name> does not exist!");
        messages.putIfAbsent(GROUP_LOAD_FAILED_MESSAGE, "<red>Something went wrong while trying to load the serverGroups!");

        //Player messages
        messages.putIfAbsent(PLAYER_LIST_MESSAGE, "<gray>Name: <yellow><name><newline><gray>UUID: <yellow><uuid><newline><click:run_command:/server <server>><gray>Server: <yellow><server></click><newline><gray>Login: <yellow><login>");
        messages.putIfAbsent(NO_PLAYERS_ONLINE_MESSAGE, "<gray>There are currently no players online!");
        messages.putIfAbsent(PLAYER_LOAD_FAILED_MESSAGE, "<red>Something went wrong while trying to load the Players!");
        messages.putIfAbsent(PLAYER_NOT_FOUND, "<gray>The player <yellow><player></yellow> is not online!");
        messages.putIfAbsent(PLAYER_INFO_MESSAGE, "<gray>Name: <yellow><name><newline><gray>UUID: <yellow><uuid><newline><gray><click:run_command:/server <server>>Server: <yellow><server></click><newline><gray>Login: <yellow><login>");
        //Player server stop/start notify
        messages.putIfAbsent(PLAYER_NOTIFY_SERVER_ONLINE, "<gray>[<green>+</green>] <click:run_command:/server <server>><yellow><server></yellow></click>");
        messages.putIfAbsent(PLAYER_NOTIFY_SERVER_OFFLINE, "<gray>[<red>-</red>] <yellow><server></yellow>");
        //Player kick messages
        messages.putIfAbsent(KICK_SERVER_MAINTENANCE, "<red>The server is currently in maintenance mode!");
        messages.putIfAbsent(KICK_NO_SERVER_FOUND, "<red>Could not find a free fallback server!");

        //Server list
        messages.putIfAbsent(MOTD, "<#09add3>A rexlCloud network<newline><gray>Welcome!");
        messages.putIfAbsent(MAINTENANCE_MOTD, "<#09add3>A rexlCloud network<newline><red>Maintenance, please come back later");

        //Server messages
        messages.putIfAbsent(START_SERVER_MESSAGE, "<gray>Starting <yellow><amount></yellow> servers from group <yellow><group></yellow>.");
        messages.putIfAbsent(ERROR_WILE_STARTING_SERVER_MESSAGE, "<red>Something went wrong while trying to start <yellow><amount></yellow> servers from group <yellow><group></yellow>!");
        messages.putIfAbsent(STOP_SERVER_MESSAGE, "<gray>Stopping server <yellow><server></yellow>.");
        messages.putIfAbsent(ERROR_WILE_STOPPING_SERVER_MESSAGE, "<red>Something went wrong while trying to stop server <yellow><server></yellow>.");
        messages.putIfAbsent(SERVER_NOT_FOUND_MESSAGE, "<red>Could not find server <yellow><server></yellow>.");
        messages.putIfAbsent(SERVER_EXECUTE_COMMAND_MESSAGE, "<gray>Executing command <yellow><command></yellow> on server <yellow><server></yellow>.");
        messages.putIfAbsent(ERROR_WILE_SERVER_EXECUTING_COMMAND_MESSAGE, "<red>Something went wrong while trying to execute command <yellow><command></yellow> on server <yellow><server></yellow>.");
        messages.putIfAbsent(SERVER_LIST_MESSAGE, "<gray>Server: <yellow><name><newline><gray>Group: <yellow><group><newline><gray>Wrapper: <yellow><wrapper><newline><gray>Port: <yellow><port><newline><gray>State: <yellow><state>");
        messages.putIfAbsent(ERROR_WILE_LOADING_SERVER_MESSAGE, "<red>Something went wrong while trying to load the servers!");

        //Wrapper messages
        messages.putIfAbsent(WRAPPER_LIST_MESSAGE, "<gray>Wrapper: <yellow><name><newline><gray>Online: <yellow><online></yellow><newline><gray>CPU: <yellow><cpu></yellow><newline><gray>Used Memory: <yellow><used_memory></yellow><newline><gray>Free Memory: <yellow><free_memory></yellow><newline><gray>Total Memory: <yellow><total_memory></yellow>");
        messages.putIfAbsent(NO_WRAPPER_FOUND_MESSAGE, "<red>Could not find any wrappers.");
        messages.putIfAbsent(ERROR_WILE_LOADING_WRAPPER_MESSAGE, "<red>Could not find wrapper <yellow><wrapper></yellow>!");
        messages.putIfAbsent(WRAPPER_INFO_MESSAGE, "<gray>Wrapper: <yellow><name><newline><gray>Online: <yellow><online></yellow><newline><gray>CPU: <yellow><cpu></yellow><newline><gray>Used Memory: <yellow><used_memory></yellow><newline><gray>Free Memory: <yellow><free_memory></yellow><newline><gray>Total Memory: <yellow><total_memory></yellow>");
        messages.putIfAbsent(STOP_WRAPPER_MESSAGE, "<gray>Stopping wrapper <yellow><wrapper></yellow>.");
    }

}
