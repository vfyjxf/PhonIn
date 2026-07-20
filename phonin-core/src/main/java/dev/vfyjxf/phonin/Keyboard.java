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
            };
}
