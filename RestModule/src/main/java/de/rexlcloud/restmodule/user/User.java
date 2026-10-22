package de.rexlcloud.restmodule.user;

import de.rexlcloud.restmodule.RestModule;
import de.rexlcloud.restmodule.user.Encryption.HashAlgorithm;
import lombok.Getter;
import lombok.NonNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
public class User {

    @NonNull
    private final String username;
    @NonNull
    private String password;
    @NonNull
    private String salt;
    @NonNull
    private HashAlgorithm hashAlgorithm;
    private final EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);

    protected User(@NonNull String username, @NonNull String password) {
        this.username = username;
        this.salt = Encryption.generateSalt();
        this.hashAlgorithm = HashAlgorithm.CURRENT;
        this.password = Encryption.hash(password, this.salt, hashAlgorithm);
    }

    protected User(@NonNull String username, @NonNull String password, @NonNull String salt,
                   @NonNull HashAlgorithm hashAlgorithm, @NonNull Collection<Permission> permissions) {
        this.username = username;
        this.salt = salt;
        this.password = password;
        this.hashAlgorithm = hashAlgorithm;
        this.permissions.addAll(permissions);
    }

    public synchronized boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public synchronized Set<Permission> getPermissions() {
        return EnumSet.copyOf(permissions);
    }

    public synchronized List<String> getPermissionNames() {
        return permissions.stream().map(Permission::name).toList();
    }

    public void addPermissions(Permission... permissions) {
        synchronized (this) {
            this.permissions.addAll(List.of(permissions));
        }
        save();
    }

    public void setPermissions(Collection<Permission> permissions) {
        synchronized (this) {
            this.permissions.clear();
            this.permissions.addAll(permissions);
        }
        save();
    }

    public void save() {
        RestModule.getInstance().getUserManager().saveUser(this);
    }

    /**
     * Checks the password. Passwords stored in an outdated hash format are re-hashed on success.
     */
    public boolean checkPassword(@NonNull String password) {
        if (!Encryption.check(password, this.password, this.salt, hashAlgorithm)) {
            return false;
        }
        if (hashAlgorithm != HashAlgorithm.CURRENT) {
            setPassword(password);
        }
        return true;
    }

    public boolean changePassword(@NonNull String newPassword, @NonNull String oldPassword) {
        if (!checkPassword(oldPassword)) {
            return false;
        }
        setPassword(newPassword);
        return true;
    }

    private void setPassword(@NonNull String newPassword) {
        synchronized (this) {
            this.salt = Encryption.generateSalt();
            this.hashAlgorithm = HashAlgorithm.CURRENT;
            this.password = Encryption.hash(newPassword, this.salt, hashAlgorithm);
        }
        save();
    }

}
