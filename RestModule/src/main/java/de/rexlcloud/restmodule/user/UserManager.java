package de.rexlcloud.restmodule.user;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import de.rexlcloud.core.config.JsonConfiguration;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.restmodule.RestModule;
import de.rexlcloud.restmodule.user.Encryption.HashAlgorithm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class UserManager {

    public static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_-]{3,32}");
    public static final int MIN_PASSWORD_LENGTH = 8;

    private final List<User> users;
    private final File userFolder;

    /**
     * The generated admin password, readable only by the cloud's user; deleted when the admin changes it.
     */
    private File initialPasswordFile() {
        return new File(RestModule.getInstance().getDataFolder(), "admin-password.txt");
    }

    private void writeInitialPassword(String password) {
        var file = initialPasswordFile().toPath();
        try {
            Files.writeString(file, password + "\n", StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows: no POSIX permissions
            }
            Console.info("[RestModule] The admin password is also in %s until it is changed", file);
        } catch (IOException e) {
            Console.warn("[RestModule] Could not save the admin password to %s: %s", file, e.getMessage());
        }
    }

    /**
     * Called after a user changed their password: the generated one is not needed any more.
     */
    public void passwordChanged(User user) {
        if (user.getUsername().equals("admin")) {
            initialPasswordFile().delete();
        }
    }

    public UserManager() {
        this.userFolder = new File(RestModule.getInstance().getDataFolder() + "/users");
        userFolder.mkdirs();
        this.users = new CopyOnWriteArrayList<>();
        loadUsers();
    }

    public void createAdminAccount() {
        if (users.isEmpty()) {
            var password = Encryption.generatePassword();
            var adminUser = createUser("admin", password);
            if (adminUser.isPresent()) {
                adminUser.get().addPermissions(Permission.values());
                // Never into the log files; the file is for reading it again later
                Console.infoOnScreenOnly("[RestModule] Created user %s with password %s", "admin", password);
                writeInitialPassword(password);
            }
        }
    }

    /**
     * Gives permissions that did not exist in the previous version to every user that may change permissions,
     * since those users could grant them to themselves anyway.
     */
    public void grantNewPermissions(Collection<Permission> newPermissions) {
        if (newPermissions.isEmpty()) {
            return;
        }
        users.stream().filter(user -> user.hasPermission(Permission.CHANGE_USER_PERMISSIONS)).forEach(user -> {
            var missing = newPermissions.stream().filter(permission -> !user.hasPermission(permission)).toList();
            if (!missing.isEmpty()) {
                user.addPermissions(missing.toArray(Permission[]::new));
                Console.info("[RestModule] Granted the new permissions %s to %s", missing, user.getUsername());
            }
        });
    }

    public List<User> getUsers() {
        return users.stream().sorted(Comparator.comparing(User::getUsername)).toList();
    }

    public Optional<User> getUser(String username) {
        return users.stream().filter(user -> user.getUsername().equals(username)).findAny();
    }

    public String[] getAllUserNames() {
        return users.stream().map(User::getUsername).toArray(String[]::new);
    }

    private void loadUsers() {
        var userFiles = userFolder.listFiles();
        if (userFiles == null) {
            return;
        }
        for (var userFile : userFiles) {
            if (!userFile.getName().endsWith(".json")) {
                continue;
            }
            try {
                var jsonObject = JsonConfiguration.loadConfiguration(userFile.toPath(), JsonObject.class);
                var username = jsonObject.get("username").getAsString();
                if (!userFile.getName().replace(".json", "").equals(username)) {
                    continue;
                }
                var password = jsonObject.get("password").getAsString();
                var salt = jsonObject.get("salt").getAsString();
                // Users saved by rexlCloud 1.x have no hash field
                var hashAlgorithm = jsonObject.has("hash")
                        ? HashAlgorithm.valueOf(jsonObject.get("hash").getAsString())
                        : HashAlgorithm.PBKDF2_SHA1_10K;
                ArrayList<String> permissionNames = JsonConfiguration.GSON().fromJson(jsonObject.get("permissions"),
                        new TypeToken<ArrayList<String>>() {
                        }.getType());
                var permissions = permissionNames == null ? List.<Permission>of() : permissionNames.stream()
                        .map(Permission::fromName).flatMap(Optional::stream).toList();
                users.add(new User(username, password, salt, hashAlgorithm, permissions));
            } catch (RuntimeException e) {
                Console.error("[RestModule] Could not load the user file %s: %s", userFile.getName(), e.getMessage());
            }
        }
    }

    public void deleteUser(User user) {
        users.remove(user);
        var file = new File(userFolder, user.getUsername() + ".json");
        if (file.exists()) {
            file.delete();
        }
    }

    public boolean existUserWithUsername(String username) {
        return users.stream().anyMatch(user -> user.getUsername().equalsIgnoreCase(username));
    }

    public void saveUser(User user) {
        var jsonObject = new JsonObject();
        synchronized (user) {
            jsonObject.addProperty("username", user.getUsername());
            jsonObject.addProperty("password", user.getPassword());
            jsonObject.addProperty("salt", user.getSalt());
            jsonObject.addProperty("hash", user.getHashAlgorithm().name());
            jsonObject.add("permissions", JsonConfiguration.GSON().toJsonTree(user.getPermissionNames()));
        }
        JsonConfiguration.saveConfiguration(new File(userFolder, user.getUsername() + ".json").toPath(), jsonObject);
    }

    /**
     * @return empty if the username is invalid or already taken
     */
    public Optional<User> createUser(String username, String password) {
        if (!USERNAME.matcher(username).matches() || existUserWithUsername(username)) {
            return Optional.empty();
        }
        var user = new User(username, password);
        users.add(user);
        saveUser(user);
        return Optional.of(user);
    }

}
