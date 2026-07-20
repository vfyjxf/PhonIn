package dev.vfyjxf.phonin.mandarin;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;

/**
 * Mandarin-specific {@link Options} presets backed by the shuangpin keymaps shipped in this module.
 */
public final class MandarinOptions {

    private MandarinOptions() {}

    /**
     * Match Mandarin against a shuangpin 2-key code (e.g. {@code "flypy"}, {@code "zrm"}).
     */
    public static Options shuangpin(String scheme) {
        return Options.builder(PhoneticSystem.mandarin)
                .keyboard(ShuangpinKeyboard.of(scheme))
                .build();
    }
}
