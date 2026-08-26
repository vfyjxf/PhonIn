package dev.vfyjxf.phonin.core.elements;

import dev.vfyjxf.phonin.core.util.IndexSet;

/**
 * A single matchable surface (plus any fuzzy / keyboard-mapped variants) for one reading of a
 * character. It holds the pre-computed set of surface strings ({@link #strs}); {@link
 * dev.vfyjxf.phonin.core.elements.CharNodes} decides what those surfaces are — the toneless syllable,
 * its fuzzy variants, and/or each variant mapped through a non-identity {@link
 * dev.vfyjxf.phonin.Keyboard}. The match pipeline is therefore policy-blind: this Phoneme only
 * compares the query against {@link #strs}.
 *
 * <p>{@link #match} returns the set of query lengths this phoneme can consume: a full surface match
 * ({@code size == surface.length()}) always counts, and in {@code partial} mode a match ending
 * exactly at the query's end mid-surface counts too (prefix / begins semantics). When abbreviation
 * is on ({@link #initLens} non-empty) a surface also matches by its abbreviation — the pinyin
 * initial under the identity keyboard (中→{@code zh}, 资→{@code z}; 2 chars for zh/ch/sh, keeping
 * them distinct from z/c/s) or the first key of the encoded surface otherwise (shuangpin
 * 中→{@code vs}→{@code v}).
 */
public final class Phoneme implements Element {

    final String[] strs;
    final int[] initLens; // abbreviation length per surface (parallel to strs); empty = off

    /**
     * @param strs the matchable surface strings (canonical first, then variants); never empty
     * @param initLens abbreviation length per surface (0 = not abbreviable), or empty to disable
     */
    public Phoneme(String[] strs, int[] initLens) {
        this.strs = strs;
        this.initLens = initLens;
    }

    @Override
    public IndexSet match(String query, int start, boolean partial) {
        IndexSet ret = new IndexSet();
        boolean abbrev = initLens.length > 0;
        for (int i = 0; i < strs.length; i++) {
            String str = strs[i];
            int size = strCmp(query, str, start);
            if (partial && start + size == query.length()) ret.set(size); // query ends here
            else if (size == str.length()) ret.set(size); // whole surface
            if (abbrev && initLens[i] > 0 && size >= initLens[i]) ret.set(initLens[i]);
        }
        return ret;
    }

    /**
     * Count leading chars of {@code surface} that match {@code query} from {@code start}.
     */
    private static int strCmp(String query, String surface, int start) {
        int len = Math.min(query.length() - start, surface.length());
        for (int i = 0; i < len; i++) {
            if (query.charAt(i + start) != surface.charAt(i)) return i;
        }
        return len;
    }

    @Override
    public String toString() {
        return strs[0];
    }
}
