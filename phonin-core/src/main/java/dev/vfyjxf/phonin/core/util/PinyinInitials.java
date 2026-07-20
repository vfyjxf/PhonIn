package dev.vfyjxf.phonin.core.util;

import dev.vfyjxf.phonin.PhoneticSystem;

/**
 * The pinyin initial (consonant onset) length of a toneless Mandarin syllable surface — the single
 * source of truth that the 简拼 (simplified-pinyin) matching mode and the Python case generator
 * ({@code tools/phonin_data/cases.py}'s {@code _initial}) both mirror. Keep them in lockstep.
 *
 * <p>{@link #length} returns the count of leading characters that form the syllable's initial:
 *
 * <ul>
 *   <li>2 for the digraph initials {@code zh}/{@code ch}/{@code sh}
 *   <li>1 for any other consonant onset ({@code b p m f d t n l g k h j q x r z c s y w})
 *   <li>0 for zero-initial (vowel-led) syllables ({@code an}, {@code ai}, {@code ou}, {@code er},
 *       …) which have no consonant to abbreviate and so contribute their full surface in 简拼
 * </ul>
 *
 * <p>This is what lets 简拼 keep {@code zh}/{@code z} distinct (low collision): 中(zhong)→{@code zh}
 * while 资(zi)→{@code z}. Merging them is the fuzzy layer's job ({@code FUZZY_ZH_Z}), not 简拼's.
 * Mandarin-specific — only {@link PhoneticSystem#mandarin} is abbreviable.
 */
public final class PinyinInitials {

    private static final String ONE_CHAR_INITIALS = "bpmfdtnlgkhjqxrzcsyw";

    private PinyinInitials() {}

    /**
     * The initial length (0, 1, or 2) of the toneless pinyin {@code surface}.
     */
    public static int length(String surface) {
        if (surface.length() >= 2) {
            char c0 = surface.charAt(0);
            char c1 = surface.charAt(1);
            if ((c0 == 'z' || c0 == 'c' || c0 == 's') && c1 == 'h') return 2;
        }
        if (!surface.isEmpty() && ONE_CHAR_INITIALS.indexOf(surface.charAt(0)) >= 0) return 1;
        return 0;
    }
}
