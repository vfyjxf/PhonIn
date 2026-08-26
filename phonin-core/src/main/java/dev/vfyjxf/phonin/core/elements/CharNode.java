package dev.vfyjxf.phonin.core.elements;

import dev.vfyjxf.phonin.core.util.IndexSet;

/**
 * The matchable lattice for one text codepoint: a literal-char fallback (the codepoint itself
 * consumes one query char when they are equal) plus one {@link Phoneme} per reading (polyphones).
 * Adapted from {@code me.towdium.pinin.elements.Char}; the codepoint is an {@code int} so
 * supplementary-plane CJK ( surrogate pairs ) are handled as a single element.
 */
public final class CharNode implements Element {

    private final int codepoint;
    private final Element[] readings; // one Phoneme per reading; empty for a literal-only node

    public CharNode(int codepoint, Element[] readings) {
        this.codepoint = codepoint;
        this.readings = readings;
    }

    @Override
    public IndexSet match(String query, int start, boolean partial) {
        // literal: a query char equal to this codepoint consumes one char. (Query is latin,
        // so this only fires for ascii-range text codepoints; CJK never equals a latin query char.)
        boolean literalMatch = start < query.length() && query.charAt(start) == codepoint;
        IndexSet ret = new IndexSet(literalMatch ? 0x2 : 0x0);
        for (Element e : readings) {
            ret.merge(e.match(query, start, partial));
        }
        return ret;
    }

    /**
     * A codepoint with no dataset entry: matches only its literal char.
     */
    public static CharNode literal(int codepoint) {
        return new CharNode(codepoint, new Element[0]);
    }
}
