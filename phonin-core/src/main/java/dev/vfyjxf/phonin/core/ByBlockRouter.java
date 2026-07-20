package dev.vfyjxf.phonin.core;

import dev.vfyjxf.phonin.CodepointRouter;
import dev.vfyjxf.phonin.PhoneticSystem;
import java.util.Arrays;

/**
 * The built-in {@link CodepointRouter}: maps each codepoint to a system by its Unicode block.
 * Ranges are used (rather than {@link Character.UnicodeBlock} constants) so supplementary-plane CJK
 * (Ext B+) is handled uniformly on the Java 8 target.
 */
public final class ByBlockRouter implements CodepointRouter {

    private final PhoneticSystem hanDefault;

    private ByBlockRouter(PhoneticSystem hanDefault) {
        if (hanDefault == null) {
            throw new IllegalArgumentException(
                    "hanDefault system is required for CJK Han codepoints");
        }
        this.hanDefault = hanDefault;
    }

    /**
     * Route by Unicode block. Hangul &rarr; {@link PhoneticSystem#korean}, kana &rarr; {@link
     * PhoneticSystem#japanese}, Bopomofo &rarr; {@link PhoneticSystem#zhuyin}, CJK Han &rarr;
     * {@code hanDefault}, everything else &rarr; {@code null}.
     */
    public static CodepointRouter byBlock(PhoneticSystem hanDefault) {
        return new ByBlockRouter(hanDefault);
    }

    @Override
    public PhoneticSystem systemFor(int cp) {
        if (isHangul(cp)) return PhoneticSystem.korean;
        if (isKana(cp)) return PhoneticSystem.japanese;
        if (isBopomofo(cp)) return PhoneticSystem.zhuyin;
        if (isHan(cp)) return hanDefault;
        return null;
    }

    @Override
    public Iterable<PhoneticSystem> knownSystems() {
        return Arrays.asList(
                hanDefault, PhoneticSystem.korean, PhoneticSystem.japanese, PhoneticSystem.zhuyin);
    }

    private static boolean isHangul(int cp) {
        return in(cp, 0xAC00, 0xD7AF) // Hangul Syllables
                || in(cp, 0x1100, 0x11FF) // Hangul Jamo
                || in(cp, 0xA960, 0xA97F) // Hangul Jamo Extended-A
                || in(cp, 0xD7B0, 0xD7FF) // Hangul Jamo Extended-B
                || in(cp, 0x3130, 0x318F); // Compat Jamo
    }

    private static boolean isKana(int cp) {
        // Hiragana, Katakana, Katakana Phonetic Extensions, Halfwidth Katakana. (The supplementary
        // Kana blocks are too obscure to risk range typos and the available Japanese data has no
        // kana;
        // supply a custom router if you need them.)
        return in(cp, 0x3040, 0x309F) // Hiragana
                || in(cp, 0x30A0, 0x30FF) // Katakana
                || in(cp, 0x31F0, 0x31FF) // Katakana Phonetic Extensions
                || in(cp, 0xFF65, 0xFF9F); // Halfwidth Katakana
    }

    private static boolean isBopomofo(int cp) {
        return in(cp, 0x3100, 0x312F) || in(cp, 0x31A0, 0x31BF); // Bopomofo + Extended
    }

    private static boolean isHan(int cp) {
        // CJK Unified Ideographs and its Extension blocks (A..G) plus Compatibility Ideographs.
        // Bounds are the Unicode block edges; non-Han plane-2/3 blocks fall in gaps between them.
        return in(cp, 0x3400, 0x4DBF) // CJK Ext A
                || in(cp, 0x4E00, 0x9FFF) // CJK Unified (+ Ext A-F ideographs tail)
                || in(cp, 0xF900, 0xFAFF) // CJK Compatibility Ideographs
                || in(cp, 0x20000, 0x2A6DF) // CJK Ext B
                || in(cp, 0x2A700, 0x2B73F) // CJK Ext C
                || in(cp, 0x2B740, 0x2B81F) // CJK Ext D
                || in(cp, 0x2B820, 0x2CEAF) // CJK Ext E
                || in(cp, 0x2CEB0, 0x2EBEF) // CJK Ext F
                || in(cp, 0x30000, 0x3134F); // CJK Ext G
    }

    private static boolean in(int cp, int lo, int hi) {
        return cp >= lo && cp <= hi;
    }
}
