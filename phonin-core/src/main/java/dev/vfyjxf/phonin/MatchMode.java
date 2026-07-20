package dev.vfyjxf.phonin;

/**
 * How the query is anchored against the text.
 */
public enum MatchMode {
    /**
     * The query occurs anywhere in the text.
     */
    CONTAINS,
    /**
     * The text starts with the query (prefix; the query may end mid-element).
     */
    BEGINS,
    /**
     * The text equals the query exactly.
     */
    EXACT
}
