package dev.vfyjxf.phonin.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vfyjxf.phonin.MatchMode;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.korean.KoreanKeyboard;
import dev.vfyjxf.phonin.korean.KoreanOptions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link KoreanKeyboard} — verifies that both layouts (2-bulsik, choseong) correctly
 * encode Hangul syllables, and that {@link Options} presets produce matchable surfaces.
 */
class KoreanKeyboardTest {

    private final PhonIn phonIn = PhonIn.create(PhoneticSystem.korean);

    @Test
    void bulsik2EncodesSyllables() {
        KoreanKeyboard kb = KoreanKeyboard.bulsik2();
        assertThat(kb.encode("가")).isEqualTo("rk");
        assertThat(kb.encode("한")).isEqualTo("gks");
        assertThat(kb.encode("중")).isEqualTo("wnd");
        assertThat(kb.encode("객차")).isEqualTo("rorck"); // multi-syllable
        assertThat(kb.isIdentity()).isFalse();
    }

    @Test
    void choseongEncodesSyllables() {
        KoreanKeyboard kb = KoreanKeyboard.choseong();
        assertThat(kb.encode("한")).isEqualTo("g"); // ㅎ → g
        assertThat(kb.encode("가")).isEqualTo("r"); // ㄱ → r
        assertThat(kb.encode("한글")).isEqualTo("gr"); // ㅎㄱ → gr
        assertThat(kb.isIdentity()).isFalse();
    }

    @Test
    void nonHangulPassesThrough() {
        KoreanKeyboard kb = KoreanKeyboard.bulsik2();
        assertThat(kb.encode("zhong")).isEqualTo("zhong"); // Mandarin pinyin
        assertThat(kb.encode("abc")).isEqualTo("abc");
    }

    @Test
    void koreanOptionMatchesBulsik2() {
        // 한 → stored as Hangul "한" → KoreanKeyboard encodes to "gks" → user types "gks"
        assertThat(phonIn.contains("한", "gks", KoreanOptions.bulsik2())).isTrue();
        assertThat(phonIn.contains("한", "han", KoreanOptions.bulsik2())).isFalse(); // not romaji
        assertThat(phonIn.contains("가", "rk", KoreanOptions.bulsik2())).isTrue();
        assertThat(phonIn.contains("중", "wnd", KoreanOptions.bulsik2())).isTrue();
    }

    @Test
    void koreanChoseongOptionMatchesInitialConsonants() {
        // choseong: 한→g, 글→r, so "gr" matches 한글
        assertThat(phonIn.contains("한", "g", KoreanOptions.choseong())).isTrue();
        assertThat(phonIn.contains("한", "gks", KoreanOptions.choseong()))
                .isFalse(); // full keys don't match
        // Multi-char: 한글 with choseong "gr"
        assertThat(phonIn.contains("한글", "gr", KoreanOptions.choseong())).isTrue();
    }

    @Test
    void koreanKeyboardActsAsIdentityForMandarin() {
        // KoreanKeyboard passes non-Hangul through, so Mandarin pinyin still matches
        Options multi =
                Options.multi(ByBlockRouter.byBlock(PhoneticSystem.mandarin)).toBuilder()
                        .keyboard(KoreanKeyboard.bulsik2())
                        .build();
        assertThat(phonIn.contains("中", "zhong", multi)).isTrue(); // Mandarin pinyin
        assertThat(phonIn.contains("한", "gks", multi)).isTrue(); // Korean 2-bulsik
    }

    @Test
    void koreanPartialMatch() {
        // Partial 2-bulsik input: "g" is prefix of "gks" (한), "gk" (하), etc.
        assertThat(phonIn.contains("한", "g", KoreanOptions.bulsik2())).isTrue();
        assertThat(phonIn.contains("한", "gk", KoreanOptions.bulsik2())).isTrue();
    }

    @Test
    void koreanExactMatch() {
        Options exact =
                Options.builder(PhoneticSystem.korean)
                        .keyboard(KoreanKeyboard.bulsik2())
                        .mode(MatchMode.EXACT)
                        .build();
        assertThat(phonIn.matches("한", "gks", exact)).isTrue();
        assertThat(phonIn.matches("한", "gk", exact)).isFalse(); // partial doesn't match exact
    }

    @Test
    void layoutNames() {
        assertThat(KoreanKeyboard.bulsik2().layout()).isEqualTo(KoreanKeyboard.Layout.BULSIK2);
        assertThat(KoreanKeyboard.choseong().layout()).isEqualTo(KoreanKeyboard.Layout.CHOSEONG);
    }

    @Test
    void toStringContainsLayout() {
        assertThat(KoreanKeyboard.bulsik2().toString()).isEqualTo("korean:bulsik2");
        assertThat(KoreanKeyboard.choseong().toString()).isEqualTo("korean:choseong");
    }
}
