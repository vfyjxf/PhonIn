package io.phonin;

/**
 * Bundled {@link Options} presets that depend on optional keyboards / routers shipped in the {@code
 * phonin-systems} module. The core {@link Options} class only knows the identity keyboard;
 * system-specific keyboards (Korean, shuangpin) and block routing live here so users who do not
 * need them can exclude {@code phonin-systems}.
 */
public final class SystemOptions {

    private SystemOptions() {}

    /** Mandarin matched against a shuangpin 2-key code (e.g. {@code "flypy"}, {@code "zrm"}). */
    public static Options mandarinShuangpin(String scheme) {
        return Options.builder(PhoneticSystem.mandarin)
                .keyboard(ShuangpinKeyboard.of(scheme))
                .build();
    }

    /**
     * Korean with the standard 2-bulsik (두벌식) keyboard. The dataset stores Hangul as the canonical
     * normalized form; the {@link KoreanKeyboard} re-maps it to the QWERTY key sequence the user
     * types (e.g. {@code rk} for 가, {@code gks} for 한).
     */
    public static Options korean() {
        return Options.builder(PhoneticSystem.korean).keyboard(KoreanKeyboard.bulsik2()).build();
    }

    /**
     * Korean with choseong-only (초성 검색) input — the user types just the initial consonant key of
     * each syllable, like Mandarin 简拼 (e.g. {@code g} for 한, {@code gr} for 한글).
     */
    public static Options koreanChoseong() {
        return Options.builder(PhoneticSystem.korean).keyboard(KoreanKeyboard.choseong()).build();
    }
}
