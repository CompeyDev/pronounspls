package xyz.devcomp.pronounspls.api;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a set of pronouns returned by PronounDB.
 * The "sets" field mirrors PronounDB's v2 API shape: a list of pronoun set keys.
 * Possible values: "he", "she", "it", "they", "any", "ask", "avoid".
 * Users may have multiple, e.g. ["she", "they"].
 */
public record Pronouns(List<String> sets) {

    /** Returns true if the user has asked not to be referred to (avoid). */
    public boolean isAvoid() {
        return sets.contains("avoid");
    }

    /** Returns true if any pronouns are acceptable. */
    public boolean isAny() {
        return sets.contains("any");
    }

    /** Returns true if the user wants to be asked. */
    public boolean isAsk() {
        return sets.contains("ask");
    }

    /**
     * Returns a human-readable representation, e.g. "he/him, they/them".
     * Falls back gracefully for special values like "ask", "any", "avoid".
     */
    public String toHumanReadable() {
        return String.join(", ", sets);
    }

    /**
     * Returns a list of translation keys representing the pronouns.
     */
    public List<String> asTranslationKeys() {
        return sets.stream()
            .map(p ->  "pronounspls.pronouns." + p)
            .collect(Collectors.toList());
    }
}
