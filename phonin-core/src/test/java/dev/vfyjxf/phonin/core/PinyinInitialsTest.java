package dev.vfyjxf.phonin.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vfyjxf.phonin.core.util.PinyinInitials;
import org.junit.jupiter.api.Test;

/**
 * Locks {@link PinyinInitials#length} — the shared logic the Java 简拼 mode and the Python case
 * generator both depend on, so a drift here breaks the Java==Python case alignment.
 */
class PinyinInitialsTest {

    @Test
    void digraphInitialsAreTwoChars() {
        assertThat(PinyinInitials.length("zhong")).isEqualTo(2);
        assertThat(PinyinInitials.length("zhuang")).isEqualTo(2);
        assertThat(PinyinInitials.length("chi")).isEqualTo(2);
        assertThat(PinyinInitials.length("cheng")).isEqualTo(2);
        assertThat(PinyinInitials.length("shan")).isEqualTo(2);
        assertThat(PinyinInitials.length("shui")).isEqualTo(2);
    }

    @Test
    void plainConsonantInitialsAreOneChar() {
        assertThat(PinyinInitials.length("guo")).isEqualTo(1);
        assertThat(PinyinInitials.length("mei")).isEqualTo(1);
        assertThat(PinyinInitials.length("wang")).isEqualTo(1); // w
        assertThat(PinyinInitials.length("yu")).isEqualTo(1); // y
        // z/c/s WITHOUT the h are one char (distinct from zh/ch/sh)
        assertThat(PinyinInitials.length("zi")).isEqualTo(1);
        assertThat(PinyinInitials.length("ci")).isEqualTo(1);
        assertThat(PinyinInitials.length("si")).isEqualTo(1);
        assertThat(PinyinInitials.length("zong")).isEqualTo(1); // fuzzy variant of zhong
    }

    @Test
    void zeroInitialsAreZero() {
        assertThat(PinyinInitials.length("an")).isEqualTo(0);
        assertThat(PinyinInitials.length("ai")).isEqualTo(0);
        assertThat(PinyinInitials.length("ou")).isEqualTo(0);
        assertThat(PinyinInitials.length("er")).isEqualTo(0);
        assertThat(PinyinInitials.length("ang")).isEqualTo(0);
        assertThat(PinyinInitials.length("a")).isEqualTo(0);
    }
}
