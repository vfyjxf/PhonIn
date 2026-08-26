package dev.vfyjxf.phonin;

/**
 * A keyboard maps a toneless syllable to the surface string the user types. The {@link #identity}
 * passes the syllable through unchanged (so the system's own romanization / Bopomofo is used);
 * shuangpin keyboards live in the {@code phonin-mandarin} module ({@code ShuangpinKeyboard}).
 * Implement this interface to plug in a custom keymap.
 *
 * <p>{@link #encode} returns the surface for a toneless syllable, or {@code null} if the syllable
 * has no representation under this keyboard (the matcher then drops that reading).
 */
public interface Keyboard {

    /**
     * Whether this keyboard leaves the system's own surface unchanged (no re-mapping).
     */
    boolean isIdentity();

    /**
     * Map a toneless syllable to this keyboard's surface, or {@code null} if the syllable has no
     * code under this keyboard (the caller drops the reading in that case).
     */
    String encode(String tonelessSyllable);

    /**
     * Normalize a raw user query before matching. The default passes the query through unchanged;
     * keyboards over a latin alphabet override this with {@link #normalizeLatin} so uppercase input
     * and the {@code ü} alias work. Case-sensitive keyboards (e.g. Korean 2-bulsik, where {@code R}
     * and {@code r} are different keys) must keep the pass-through.
     */
    default String normalizeQuery(String query) {
        return query;
    }

    /**
     * Lowercase ASCII letters and map {@code ü}/{@code Ü} to {@code v} (the standard pinyin ü
     * alias). Zero-allocation when the query is already normalized.
     */
    static String normalizeLatin(String query) {
        int n = query.length();
        for (int i = 0; i < n; i++) {
            char c = query.charAt(i);
            if ((c >= 'A' && c <= 'Z') || c == 'ü' || c == 'Ü') {
                StringBuilder b = new StringBuilder(n).append(query, 0, i);
                for (int j = i; j < n; j++) {
                    char d = query.charAt(j);
                    if (d >= 'A' && d <= 'Z') d += 'a' - 'A';
                    else if (d == 'ü' || d == 'Ü') d = 'v';
                    b.append(d);
                }
                return b.toString();
            }
        }
        return query;
    }

    /**
     * The pass-through keyboard: the system's surface is used as-is.
     */
    Keyboard identity =
            new Keyboard() {
                @Override
                public boolean isIdentity() {
                    return true;
                }

                @Override
                public String encode(String tonelessSyllable) {
                    return tonelessSyllable;
                }

                @Override
                public String normalizeQuery(String query) {
                    return normalizeLatin(query);
                }
            };
}
