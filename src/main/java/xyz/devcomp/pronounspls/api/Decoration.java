package xyz.devcomp.pronounspls.api;

import java.util.Set;

/**
 * Represents the equipped decoration returned by PronounDB.
 */
public record Decoration(String name) {
    private static final Set<String> PRIDE_DECORATIONS = Set.of(
        "pride",
        "pride_bisexual",
        "pride_lesbian",
        "pride_pansexual",
        "pride_transgender"
    );

    /** Returns true if the decoration is pride themed.. */
    public boolean isPride() {
        return PRIDE_DECORATIONS.contains(name);
    }
}
