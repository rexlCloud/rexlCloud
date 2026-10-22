package de.rexlcloud.restmodule.user;

import java.util.Arrays;
import java.util.Optional;

public enum Permission {

    CREATE_USER,
    DELETE_USER,
    CHANGE_USER_PERMISSIONS,
    SEE_ALL_SERVERS,
    SEE_ALL_USERS,
    SEE_ALL_GROUPS,
    START_SERVER,
    STOP_SERVER,
    EXECUTE_ON_SERVER,
    SEE_SERVER_CONSOLE,
    MANAGE_GROUPS,
    SEE_PLAYERS,
    MANAGE_PLAYERS,
    SEE_WRAPPERS,
    MANAGE_WRAPPERS,
    MANAGE_MAINTENANCE,
    MANAGE_MODULES,
    /**
     * Read and change template files, and save running servers into templates.
     */
    MANAGE_TEMPLATES;

    public static Optional<Permission> fromName(String name) {
        return Arrays.stream(values()).filter(permission -> permission.name().equalsIgnoreCase(name)).findFirst();
    }

}
