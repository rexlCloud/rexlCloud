package de.rexlcloud.core.api.module;

import com.google.gson.JsonElement;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of a state-changing {@link ModuleApi} call.
 *
 * @param status  what happened
 * @param message human-readable explanation, mainly for errors
 * @param data    optional payload (for example the generated key of a new wrapper)
 */
public record ModuleApiResult(@NonNull Status status, @Nullable String message, @Nullable JsonElement data) {

    public enum Status {
        /** The action was executed. */
        OK,
        /** The action was accepted and continues in the background. */
        ACCEPTED,
        /** The referenced server, group, player, wrapper or module does not exist. */
        NOT_FOUND,
        /** The action conflicts with the current state (e.g. name already taken). */
        CONFLICT,
        /** The input was invalid. */
        INVALID
    }

    public static ModuleApiResult ok() {
        return new ModuleApiResult(Status.OK, null, null);
    }

    public static ModuleApiResult ok(JsonElement data) {
        return new ModuleApiResult(Status.OK, null, data);
    }

    public static ModuleApiResult ok(String message) {
        return new ModuleApiResult(Status.OK, message, null);
    }

    public static ModuleApiResult accepted(String message) {
        return new ModuleApiResult(Status.ACCEPTED, message, null);
    }

    public static ModuleApiResult notFound(String message) {
        return new ModuleApiResult(Status.NOT_FOUND, message, null);
    }

    public static ModuleApiResult conflict(String message) {
        return new ModuleApiResult(Status.CONFLICT, message, null);
    }

    public static ModuleApiResult invalid(String message) {
        return new ModuleApiResult(Status.INVALID, message, null);
    }

    public boolean isSuccess() {
        return status == Status.OK || status == Status.ACCEPTED;
    }

}
