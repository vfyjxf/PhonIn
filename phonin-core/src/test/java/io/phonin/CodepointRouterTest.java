package io.phonin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.phonin.fuzzy.FuzzyRules;
import io.phonin.search.Searcher;
import io.phonin.search.SearcherLogic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * MULTI mode: a {@link CodepointRouter} routes each text codepoint to the system that claims it, so
 * one match call handles mixed-script text. The correctness invariant is that routing a codepoint
 * to system S is equivalent to matching it under single-system S — so every MULTI assertion is
 * cross-checked against the equivalent single-system call.
 */
class CodepointRouterTest {

    private static PhonIn phonIn;

    @BeforeAll
    static void load() {
        phonIn = PhonIn.createAll();
    }

    @Test
    void byBlockRoutesByUnicodeBlock() {
        CodepointRouter r = ByBlockRouter.byBlock(PhoneticSystem.mandarin);
        assertThat(r.systemFor(0xD55C)).isSameAs(PhoneticSystem.korean); // 한 (Hangul)
        assertThat(r.systemFor(0x306B)).isSameAs(PhoneticSystem.japanese); // に (Hiragana)
        assertThat(r.systemFor(0x30AB)).isSameAs(PhoneticSystem.japanese); // カ (Katakana)
        assertThat(r.systemFor(0x3105)).isSameAs(PhoneticSystem.zhuyin); // ㄅ (Bopomofo)
        assertThat(r.systemFor(0x4E2D)).isSameAs(PhoneticSystem.mandarin); // 中 (Han -> default)
        assertThat(r.systemFor(0x2A6A5))
                .isSameAs(PhoneticSystem.mandarin); // 𪚥 (supplementary Han)
        assertThat(r.systemFor('a')).isNull(); // Latin -> literal
        assertThat(r.systemFor('1')).isNull();
    }

    @Test
    void byBlockHanDefaultIsConfigurable() {
        assertThat(ByBlockRouter.byBlock(PhoneticSystem.japanese).systemFor(0x4E2D))
                .isSameAs(PhoneticSystem.japanese);
        assertThat(ByBlockRouter.byBlock(PhoneticSystem.cantonese).systemFor(0x4E2D))
                .isSameAs(PhoneticSystem.cantonese);
    }

    @Test
    void multiMatchesRoutedSystemOnly() {
        // 中: mandarin reads "zhong"; japanese reads "naka"/"chuu"/... Routing picks which matches.
        Options multiMd = Options.multi(ByBlockRouter.byBlock(PhoneticSystem.mandarin));
        Options multiJp = Options.multi(ByBlockRouter.byBlock(PhoneticSystem.japanese));
        Options md = Options.mandarinQuanpin();
        Options jp = Options.japaneseRomaji();

        // hanDefault=mandarin: 中 routes to mandarin -> "zhong" matches, "naka" does not.
        assertThat(phonIn.contains("中", "zhong", multiMd))
                .isEqualTo(phonIn.contains("中", "zhong", md))
                .isTrue();
        assertThat(phonIn.contains("中", "naka", multiMd))
                .isEqualTo(phonIn.contains("中", "naka", md))
                .isFalse();

        // hanDefault=japanese: 中 routes to japanese -> "naka" matches, "zhong" does not.
        assertThat(phonIn.contains("中", "naka", multiJp))
                .isEqualTo(phonIn.contains("中", "naka", jp))
                .isTrue();
        assertThat(phonIn.contains("中", "zhong", multiJp))
                .isEqualTo(phonIn.contains("中", "zhong", jp))
                .isFalse();
    }

    @Test
    void multiRoutesHangulToKoreanRegardlessOfHanDefault() {
        // KoreanKeyboard acts as identity for non-Hangul (Mandarin pinyin passes through), so it's
        // safe to use in multi mode — Mandarin matches by pinyin, Korean by 2-bulsik keys.
        Options multi =
                Options.multi(ByBlockRouter.byBlock(PhoneticSystem.mandarin)).toBuilder()
                        .keyboard(KoreanKeyboard.bulsik2())
                        .build();
        // 한 routes to korean -> matches its 2-bulsik keyboard sequence "gks".
        assertThat(phonIn.contains("한", "gks", multi))
                .isEqualTo(phonIn.contains("한", "gks", SystemOptions.korean()))
                .isTrue();
    }

    @Test
    void multiLiteralFallbackForUnroutedChars() {
        Options multi = Options.multi(ByBlockRouter.byBlock(PhoneticSystem.mandarin));
        assertThat(phonIn.contains("abc", "b", multi)).isTrue(); // latin chars -> literal
        assertThat(phonIn.contains("abc", "x", multi)).isFalse();
    }

    @Test
    void multiMixedScriptText() {
        // "中한": 中->mandarin, 한->korean. Each char matched by its own system within one call.
        // KoreanKeyboard passes non-Hangul through, so Mandarin pinyin still works.
        Options multi =
                Options.multi(ByBlockRouter.byBlock(PhoneticSystem.mandarin)).toBuilder()
                        .keyboard(KoreanKeyboard.bulsik2())
                        .build();
        assertThat(phonIn.contains("中한", "zhong", multi)).isTrue(); // 中 by mandarin
        assertThat(phonIn.contains("中한", "gks", multi)).isTrue(); // 한 by korean (2-bulsik "gks")
        assertThat(phonIn.contains("中한", "naka", multi)).isFalse(); // 中 not routed to japanese
    }

    @Test
    void multiComposesWithShuangpinAndFuzzy() {
        // toBuilder carries the router, so MULTI composes with shuangpin / fuzzy; each must match
        // the
        // equivalent single-system (routed) counterpart.
        Options multiSp =
                Options.multi(ByBlockRouter.byBlock(PhoneticSystem.mandarin)).toBuilder()
                        .keyboard(ShuangpinKeyboard.of("flypy"))
                        .build();
        Options singleSp = SystemOptions.mandarinShuangpin("flypy");
        assertThat(phonIn.contains("中", "vs", multiSp)) // 中 -> mandarin -> flypy code "vs"
                .isEqualTo(phonIn.contains("中", "vs", singleSp))
                .isTrue();
        assertThat(phonIn.contains("中", "zs", multiSp))
                .isEqualTo(phonIn.contains("中", "zs", singleSp))
                .isFalse();

        Options multiFz =
                Options.multi(ByBlockRouter.byBlock(PhoneticSystem.mandarin)).toBuilder()
                        .addFuzzy(FuzzyRules.fuzzyZhZ)
                        .build();
        Options singleFz =
                Options.builder(PhoneticSystem.mandarin).addFuzzy(FuzzyRules.fuzzyZhZ).build();
        // 中=zhong; fuzzy zh->z exposes "zong" under both MULTI and single.
        assertThat(phonIn.contains("中", "zong", multiFz))
                .isEqualTo(phonIn.contains("中", "zong", singleFz))
                .isTrue();
    }

    @Test
    void multiWorksThroughSearcherAndCompile() {
        // The router is honored by the Searcher / Accelerator paths too (they go via MatchContext).
        Options multi =
                Options.multi(ByBlockRouter.byBlock(PhoneticSystem.mandarin)).toBuilder()
                        .keyboard(KoreanKeyboard.bulsik2())
                        .build();
        Searcher<String> s = phonIn.searcher(SearcherLogic.CONTAIN, multi);
        s.put("中한", "id1");
        assertThat(s.search("zhong")).containsExactlyInAnyOrder("id1");
        assertThat(s.search("gks")).containsExactlyInAnyOrder("id1"); // 한 2-bulsik keyboard
        assertThat(s.search("naka")).isEmpty();

        AcceleratedQuery q = phonIn.compile("zhong", multi);
        assertThat(q.contains("中한")).isTrue();
    }

    @Test
    void multiBuildValidation() {
        Options multi = Options.multi(ByBlockRouter.byBlock(PhoneticSystem.mandarin));
        assertThat(multi.system()).isNull();
        assertThat(multi.router()).isNotNull();

        Options single = Options.mandarinQuanpin();
        assertThat(single.system()).isNotNull();
        assertThat(single.router()).isNull();

        // toBuilder preserves the MULTI regime.
        Options rebuilt = multi.toBuilder().build();
        assertThat(rebuilt.router()).isNotNull();
        assertThat(rebuilt.system()).isNull();

        assertThatThrownBy(() -> Options.multi(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Options.builder(null).build())
                .isInstanceOf(IllegalStateException.class); // neither system nor router
    }
}
