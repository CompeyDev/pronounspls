package xyz.devcomp.pronounspls.api;

import java.util.Set;

/**
 * Represents the equipped decoration returned by PronounDB.
 */
public record Decoration(String name) {
    private static final Set<String> PRIDE_DECORATIONS = Set.of(
        "pride",
        "pride_bi",
        "pride_lesbian",
        "pride_pan",
        "pride_trans"
    );

    /** Returns true if the decoration is pride themed.. */
    public boolean isPride() {
        return PRIDE_DECORATIONS.contains(name);
    }
}
