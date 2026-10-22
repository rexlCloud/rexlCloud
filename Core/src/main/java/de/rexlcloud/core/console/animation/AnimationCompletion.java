package de.rexlcloud.core.console.animation;

import org.jetbrains.annotations.Nullable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

/**
 * An answer offered with Tab, optionally with a description shown next to it.
 */
@Getter
@AllArgsConstructor
public class AnimationCompletion {

    /**
     * The text inserted by Tab.
     */
    @NonNull
    private String completion;
    /**
     * Shown next to the completion, {@code null} for none.
     */
    @Nullable
    private String description;

    public AnimationCompletion(@NonNull String completion) {
        this.completion = completion;
        description = null;
    }

}
