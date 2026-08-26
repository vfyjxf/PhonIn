package dev.vfyjxf.phonin.mandarin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.vfyjxf.phonin.AbbrevPolicy;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.core.PhonIn;
import dev.vfyjxf.phonin.core.fuzzy.FuzzyRules;
import org.junit.jupiter.api.Test;

/**
 * Shuangpin regression tests. Headline case: the JEC #188 zero-initial bug — a zero-initial
 * syllable is typed with its leading zero key under every scheme (安 = {@code aj} in flypy/zrm,
 * {@code oj} in mspy/abc, {@code of} in pyjj), and that code — not the bare final — must match.
 */
class ShuangpinKeyboardTest {

    private final PhonIn phonIn = PhonIn.create(PhoneticSystem.mandarin);

    private final Options flypy = MandarinOptions.shuangpin("flypy");
    private final Options zrm = MandarinOptions.shuangpin("zrm");
    private final Options mspy = MandarinOptions.shuangpin("mspy");
    private final Options abc = MandarinOptions.shuangpin("abc");
    private final Options pyjj = MandarinOptions.shuangpin("pyjj");

    @Test
    void zeroInitialCodesMatch() {
        assertThat(phonIn.contains("安", "aj", flypy)).isTrue();
        assertThat(phonIn.contains("安", "aj", zrm)).isTrue();
        assertThat(phonIn.contains("安", "oj", mspy)).isTrue();
        assertThat(phonIn.contains("安", "oj", abc)).isTrue();
        assertThat(phonIn.contains("安", "of", pyjj)).isTrue();

        assertThat(phonIn.contains("哦", "oo", flypy)).isTrue();
        assertThat(phonIn.contains("哦", "oo", mspy)).isTrue();
        assertThat(phonIn.contains("二", "er", flypy)).isTrue();
        assertThat(phonIn.contains("二", "or", mspy)).isTrue();
        assertThat(phonIn.contains("二", "oq", pyjj)).isTrue();
        assertThat(phonIn.contains("阿", "aa", flypy)).isTrue();
        assertThat(phonIn.contains("阿", "oa", mspy)).isTrue();
        assertThat(phonIn.contains("欧", "oz", flypy)).isTrue();
        assertThat(phonIn.contains("欧", "ob", mspy)).isTrue();
        assertThat(phonIn.contains("欧", "op", pyjj)).isTrue();

        // the bare final must NOT match: typing "an" is not how mspy spells 安
        assertThat(phonIn.contains("安", "an", mspy)).isFalse();
        assertThat(phonIn.contains("安", "an", flypy)).isFalse();
    }

    @Test
    void zeroInitialWordsMatch() {
        // 安山岩 = an shan yan
        assertThat(phonIn.contains("安山岩", "ajujyj", flypy)).isTrue();
        assertThat(phonIn.contains("安山岩", "ajujyj", zrm)).isTrue();
        assertThat(phonIn.contains("安山岩", "ojujyj", mspy)).isTrue();
        assertThat(phonIn.contains("安山岩", "ofifyf", pyjj)).isTrue();
    }

    @Test
    void queryIsNormalized() {
        assertThat(phonIn.contains("中国", "VSGO", flypy)).isTrue();
        assertThat(phonIn.contains("庆", "Q;", mspy)).isTrue(); // ';' survives normalization
        assertThat(phonIn.contains("绿", "lü", Options.mandarinQuanpin())).isTrue(); // ü alias
        assertThat(phonIn.contains("绿", "lv", Options.mandarinQuanpin())).isTrue();
        assertThat(phonIn.contains("女", "nv", flypy)).isTrue();
        assertThat(phonIn.contains("女", "ny", mspy)).isTrue();
        assertThat(phonIn.contains("居", "jv", flypy)).isTrue();
        assertThat(phonIn.contains("居", "jy", mspy)).isTrue();
    }

    @Test
    void fuzzyAppliesBeforeEncoding() {
        // 章 zhang -> vh; FUZZY_ANG_AN adds zhan -> vj
        Options fuzzyAng = flypy.toBuilder().addFuzzy(FuzzyRules.fuzzyAngAn).build();
        assertThat(phonIn.contains("章", "vh", fuzzyAng)).isTrue();
        assertThat(phonIn.contains("章", "vj", fuzzyAng)).isTrue();
        assertThat(phonIn.contains("章", "vj", flypy)).isFalse(); // inert without the rule
        // 重庆: 重 also reads chong -> FUZZY_CH_C adds cong(cs) -> "csqk" (qing -> qk)
        Options fuzzyChC = flypy.toBuilder().addFuzzy(FuzzyRules.fuzzyChC).build();
        assertThat(phonIn.contains("重庆", "isqk", fuzzyChC)).isTrue();
        assertThat(phonIn.contains("重庆", "csqk", fuzzyChC)).isTrue();
        assertThat(phonIn.contains("重庆", "csqk", flypy)).isFalse();
    }

    @Test
    void abbrevMatchesFirstKeyOfCode() {
        // 首键简拼: 中国 = vs+go -> "vg" (flypy/zrm/mspy/pyjj), as+go -> "ag" (abc)
        Options flypyAbbrev = flypy.toBuilder().abbrev(AbbrevPolicy.INITIALS).build();
        Options abcAbbrev = abc.toBuilder().abbrev(AbbrevPolicy.INITIALS).build();
        assertThat(phonIn.contains("中国", "vg", flypyAbbrev)).isTrue();
        assertThat(phonIn.contains("中国", "ag", abcAbbrev)).isTrue();
        assertThat(phonIn.contains("中国", "zg", flypyAbbrev)).isFalse(); // 中's key is v, not z
        assertThat(phonIn.contains("中国", "vg", flypy)).isFalse(); // off without INITIALS
    }

    @Test
    void unknownSchemeFailsLoudly() {
        assertThatThrownBy(() -> ShuangpinKeyboard.of("nope"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nope");
    }
}
