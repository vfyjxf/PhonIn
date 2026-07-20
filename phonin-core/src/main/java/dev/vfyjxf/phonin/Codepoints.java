package dev.vfyjxf.phonin;

import java.util.Arrays;

/**
 * Codepoint parsing / formatting helpers used across modules. Keeps the char-table and polyphone
 * loaders (and the matching engine) from duplicating codepoint utility logic.
 */
public final class Codepoints {

    private Codepoints() {}

    /**
     * Convert a Java {@link String} into its codepoint array, handling surrogate pairs.
     */
    public static int[] toCodepoints(String text) {
        int len = text.length();
        int[] out = new int[len];
        int c = 0;
        for (int i = 0; i < len; ) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < len) {
                char low = text.charAt(i + 1);
                if (Character.isLowSurrogate(low)) {
                    out[c++] = Character.toCodePoint(ch, low);
                    i += 2;
                    continue;
                }
            }
            out[c++] = ch;
            i++;
        }
        if (c == len) return out;
        return Arrays.copyOf(out, c);
    }

    /**
     * Reconstruct a {@link String} from a codepoint array.
     */
    public static String fromCodepoints(int[] cps) {
        StringBuilder sb = new StringBuilder(cps.length);
        for (int cp : cps) sb.appendCodePoint(cp);
        return sb.toString();
    }

    /**
     * Parse an {@code U+XXXX} / {@code u+xxxx} codepoint token; return -1 for invalid input.
     */
    public static int parseCodepoint(String s) {
        if (s.length() < 3 || (s.charAt(0) != 'U' && s.charAt(0) != 'u') || s.charAt(1) != '+')
            return -1;
        try {
            return Integer.parseInt(s.substring(2), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Parse a comma-separated list of {@code U+XXXX} tokens; return null on any error.
     */
    public static int[] parseCodepoints(String s) {
        String[] parts = s.split(",");
        int[] cps = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int cp = parseCodepoint(parts[i].trim());
            if (cp < 0) return null;
            cps[i] = cp;
        }
        return cps;
    }

    /**
     * Format a codepoint as {@code U+XXXX}.
     */
    public static String formatCodepoint(int cp) {
        return String.format("U+%04X", cp);
    }
}
