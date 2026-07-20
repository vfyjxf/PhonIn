package io.phonin.elements;

import io.phonin.util.IndexSet;
import io.phonin.util.PinyinInitials;

/**
 * A single matchable surface (plus any fuzzy / keyboard-mapped variants) for one reading of a
 * character. It holds the pre-computed set of surface strings ({@link #strs}); {@link
 * io.phonin.match.MatchContext} decides what those surfaces are — the toneless syllable, its fuzzy
 * variants (from {@code fuzzy.py}, applied to the toneless syllable), and/or each variant mapped
 * through a non-identity {@link io.phonin.Keyboard}. The match pipeline is therefore policy-blind:
 * this Phoneme only compares the query against {@link #strs}.
 *
 * <p>Adapted from {@code me.towdium.pinin.elements.Phoneme}. PhonIn collapses a syllable to one
 * Phoneme whose surface is the whole matchable string (the toneless syllable by default, or the
 * tone-marked normalized form / shuangpin code depending on {@link io.phonin.Options}).
 *
 * <p>{@link #match} returns the set of query lengths this phoneme can consume: a full surface match
 * ({@code size == surface.length()}) always counts, and in {@code partial} mode a match ending
 * exactly at the query's end mid-surface counts too (prefix / begins semantics). When {@link
 * #sequence} is set (简拼 / initials mode) the syllable's full pinyin initial also matches — 中→zh,
 * 资→z (2 chars for zh/ch/sh, 1 for other consonants, none for zero-initial syllables like 安) —
 * consuming the initial's length. zh/ch/sh thus stay distinct from z/c/s (low collision); merging
 * them is the fuzzy layer's job ({@code FUZZY_ZH_Z} etc.).
 */
public final class Phoneme implements Element {

    final String[] strs;
    final int[] initLens; // pinyin-initial length per surface (parallel to strs); 0 = zero-initial
    final boolean sequence;

    /**
     * @param strs the matchable surface strings (canonical first, then variants); never empty
     * @param sequence whether 简拼 (initials) mode is active
     */
    public Phoneme(String[] strs, boolean sequence) {
        this.strs = strs;
        this.initLens = sequence ? new int[strs.length] : new int[0];
        if (sequence) {
            for (int i = 0; i < strs.length; i++) initLens[i] = PinyinInitials.length(strs[i]);
        }
        this.sequence = sequence;
    }

    @Override
    public IndexSet match(String query, int start, boolean partial) {
        IndexSet ret = new IndexSet();
        for (int i = 0; i < strs.length; i++) {
            String str = strs[i];
            int size = strCmp(query, str, start);
            if (partial && start + size == query.length()) ret.set(size); // query ends here
            else if (size == str.length()) ret.set(size); // whole surface
            // 简拼: the syllable's full pinyin initial also matches (中=zh, 资=z), consuming exactly
            // initLens chars. zh/ch/sh stay distinct from z/c/s (far lower collision than matching
            // every syllable to one char); fuzzy variants in strs expand the initial set
            // (中+FUZZY_ZH_Z → {zh, z}), so merging z/zh is the fuzzy layer's job, not 简拼's.
            if (sequence && initLens[i] > 0 && size >= initLens[i]) ret.set(initLens[i]);
        }
        return ret;
    }

    /** Count leading chars of {@code surface} that match {@code query} from {@code start}. */
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
