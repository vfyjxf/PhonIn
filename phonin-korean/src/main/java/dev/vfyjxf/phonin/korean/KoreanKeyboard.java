package dev.vfyjxf.phonin.korean;

import dev.vfyjxf.phonin.Keyboard;
import java.util.Objects;

/**
 * A {@link Keyboard} for Korean that maps Hangul syllables to the surface string the user types
 * under a given input method. The dataset stores Hangul as the canonical normalized form (e.g.
 * {@code "한"}), and this keyboard re-maps it to the key sequence the user types.
 *
 * <p>Supported layouts:
 *
 * <ul>
 *   <li><b>2-bulsik (두벌식)</b> — the standard Korean keyboard. Consonants and vowels share the same
 *       key group; the IME composes syllables from the sequence. E.g. {@code 한 → "gks"}.
 *   <li><b>Choseong (초성)</b> — initial-consonant-only search, like Mandarin 简拼. Each syllable maps
 *       to just its choseong key. E.g. {@code 한글 → "gr"} (ㅎ=g, ㄱ=r in 2-bulsik).
 * </ul>
 *
 * <p>Multi-syllable strings are handled by encoding each syllable independently and concatenating
 * the results. Non-Hangul characters are passed through unchanged.
 *
 * <p>Instances are immutable and cached. Use the static factory methods ({@link #bulsik2()}, {@link
 * #choseong()}) to obtain an instance.
 */
public final class KoreanKeyboard implements Keyboard {

    /**
     * The input method layout.
     */
    public enum Layout {
        /**
         * Standard 2-bulsik (두벌식) keyboard.
         */
        BULSIK2,
        /**
         * Choseong-only (초성 검색) — initial consonant keys only, like 简拼.
         */
        CHOSEONG
    }

    //region 2-bulsik key tables (same as tools/phonin_data/korean.py)

    private static final String[] CHO_KEY = {
        "r", "R", "s", "e", "E", "f", "a", "q", "Q", "t", "T", "d", "w", "W", "c", "z", "x", "v",
        "g"
    };
    private static final String[] JUNG_KEY = {
        "k", "o", "i", "O", "j", "p", "u", "P", "h", "hk", "ho", "hl", "y", "n", "nj", "np", "nl",
        "b", "m", "ml", "l"
    };
    private static final String[] JONG_KEY = {
        "", "r", "R", "rt", "s", "sw", "sg", "e", "f", "fr", "fa", "fq",
        "ft", "fx", "fv", "fg", "a", "q", "qt", "t", "T", "d", "w", "c",
        "z", "x", "v", "g"
    };

    private static final int HANGUL_START = 0xAC00;
    private static final int HANGUL_END = 0xD7A3;
    private static final int HANGUL_COUNT = HANGUL_END - HANGUL_START + 1;

    //endregion
    //region cached instances

    private static final KoreanKeyboard BULSIK2 = new KoreanKeyboard(Layout.BULSIK2);
    private static final KoreanKeyboard CHOSEONG = new KoreanKeyboard(Layout.CHOSEONG);

    private final Layout layout;
    // Lazy per-syllable cache: the common encode() case is a single Hangul syllable.
    private final String[] singleCache = new String[HANGUL_COUNT];

    private KoreanKeyboard(Layout layout) {
        this.layout = layout;
    }

    /**
     * The standard 2-bulsik (두벌식) keyboard — the most common Korean input method.
     */
    public static KoreanKeyboard bulsik2() {
        return BULSIK2;
    }

    /**
     * Choseong-only (초성 검색) — type just the initial consonant of each syllable, like 简拼.
     */
    public static KoreanKeyboard choseong() {
        return CHOSEONG;
    }

    /**
     * The layout this keyboard uses.
     */
    public Layout layout() {
        return layout;
    }

    @Override
    public boolean isIdentity() {
        return false;
    }

    @Override
    public String encode(String hangul) {
        Objects.requireNonNull(hangul, "hangul");
        // Fast path for the single-syllable case (most common in MatchContext.surfacesFor).
        if (hangul.length() == 1) {
            char ch = hangul.charAt(0);
            if (ch >= HANGUL_START && ch <= HANGUL_END) {
                String cached = singleCache[ch - HANGUL_START];
                if (cached == null) {
                    cached = encodeSingle(ch);
                    singleCache[ch - HANGUL_START] = cached;
                }
                return cached;
            }
            return hangul; // single non-Hangul char: pass through
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < hangul.length(); i++) {
            char ch = hangul.charAt(i);
            int cp = ch;
            if (cp >= HANGUL_START && cp <= HANGUL_END) {
                out.append(encodeSingle(ch));
            } else {
                out.append(ch); // non-Hangul: pass through
            }
        }
        String result = out.toString();
        return result.isEmpty() ? null : result;
    }

    private String encodeSingle(char ch) {
        int s = ch - HANGUL_START;
        int l = s / 588; // choseong index (0..18)
        int v = (s % 588) / 28; // jungseong index (0..20)
        int t = s % 28; // jongseong index (0..27)
        switch (layout) {
            case BULSIK2:
                return CHO_KEY[l] + JUNG_KEY[v] + JONG_KEY[t];
            case CHOSEONG:
                return CHO_KEY[l];
            default:
                throw new IllegalStateException("Unknown layout: " + layout);
        }
    }

    @Override
    public String toString() {
        return "korean:" + layout.name().toLowerCase();
    }
    //endregion
}
