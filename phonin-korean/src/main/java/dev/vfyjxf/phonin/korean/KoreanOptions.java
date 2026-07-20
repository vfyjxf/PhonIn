package dev.vfyjxf.phonin.korean;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;

/**
 * Korean-specific {@link Options} presets backed by the 2-bulsik / choseong keyboards shipped in
 * this module.
 */
public final class KoreanOptions {

    private KoreanOptions() {}

    /**
     * Korean with the standard 2-bulsik (두벌식) keyboard. The dataset stores Hangul as the canonical
     * normalized form; the {@link KoreanKeyboard} re-maps it to the QWERTY key sequence the user
     * types (e.g. {@code rk} for 가, {@code gks} for 한).
     */
    public static Options bulsik2() {
        return Options.builder(PhoneticSystem.korean).keyboard(KoreanKeyboard.bulsik2()).build();
    }

    /**
     * Korean with choseong-only (초성 검색) input — the user types just the initial consonant key of
     * each syllable, like Mandarin 简拼 (e.g. {@code g} for 한, {@code gr} for 한글).
     */
    public static Options choseong() {
        return Options.builder(PhoneticSystem.korean).keyboard(KoreanKeyboard.choseong()).build();
    }
}
