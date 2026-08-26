package dev.vfyjxf.phonin.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vfyjxf.phonin.AbbrevPolicy;
import dev.vfyjxf.phonin.Keyboard;
import dev.vfyjxf.phonin.MatchMode;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.ToneConvention;
import dev.vfyjxf.phonin.TonePolicy;
import dev.vfyjxf.phonin.core.fuzzy.FuzzyRules;
import dev.vfyjxf.phonin.core.util.IndexSet;
import dev.vfyjxf.phonin.mandarin.MandarinOptions;
import dev.vfyjxf.phonin.mandarin.ShuangpinKeyboard;
import dev.vfyjxf.phonin.model.CharEntry;
import dev.vfyjxf.phonin.model.Reading;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Focused unit tests for the matching engine itself: the branches the case-corpus test does not
 * reach — {@link MatchMode#BEGINS}/{@link MatchMode#EXACT}, abbreviation (sequence), {@link
 * IndexSet}'s bitmap ops, {@link ToneConvention}, and the model holders. These pin the engine's
 * behaviour on crafted inputs and lift coverage above the JaCoCo gate.
 */
class PhonInEngineTest {

    private static PhonIn phonIn;

    @BeforeAll
    static void load() {
        phonIn = PhonIn.create(PhoneticSystem.mandarin);
    }

    //region match modes & multi-char text

    @Test
    void containsBeginsExactOnMultiCharText() {
        Options o = Options.mandarinQuanpin();
        // "中国": 中=zhong, 国=guo
        assertThat(phonIn.contains("中国", "zhong", o)).isTrue(); // prefix inside the text
        assertThat(phonIn.contains("中国", "guo", o)).isTrue(); // suffix inside the text
        assertThat(phonIn.contains("中国", "ongg", o)).isFalse(); // surface must match from its start
        assertThat(phonIn.begins("中国", "zhong", o)).isTrue();
        assertThat(phonIn.begins("中国", "guo", o)).isFalse(); // not a prefix
        assertThat(phonIn.matches("中国", "zhongguo", o)).isTrue(); // whole text == whole query
        assertThat(phonIn.matches("中国", "zhong", o)).isFalse(); // query does not consume all text
        assertThat(phonIn.matches("中", "zhong", o)).isTrue(); // single char exact
    }

    @Test
    void partialSyllableMatchesInContainsAndBegins() {
        Options o = Options.mandarinQuanpin();
        assertThat(phonIn.contains("中", "zho", o)).isTrue(); // prefix of "zhong"
        assertThat(phonIn.begins("中", "zh", o)).isTrue();
        assertThat(phonIn.matches("中", "zho", o)).isFalse(); // exact rejects the prefix
    }

    @Test
    void abbreviationMatchesInitials() {
        Options o = Options.builder(PhoneticSystem.mandarin).abbrev(AbbrevPolicy.INITIALS).build();
        assertThat(o.sequence()).isTrue();
        // 简拼 = full pinyin initial: 中=zh, 国=g -> "zhg". zh/ch/sh stay 2 chars (low collision),
        // distinct from z-initial syllables like 资(zi)=z.
        assertThat(phonIn.contains("中国", "zhg", o)).isTrue();
        assertThat(phonIn.contains("中国", "zg", o)).isFalse(); // tightened: 中 no longer matches 'z'
        assertThat(phonIn.contains("中国", "gz", o)).isFalse();
        // z/zh merging is the fuzzy layer's job: FUZZY_ZH_Z -> 中 has a zong variant -> "zg"
        // matches.
        Options fuzzy =
                Options.builder(PhoneticSystem.mandarin)
                        .abbrev(AbbrevPolicy.INITIALS)
                        .addFuzzy(FuzzyRules.fuzzyZhZ)
                        .build();
        assertThat(phonIn.contains("中国", "zg", fuzzy)).isTrue();
        // abbreviation is ignored for systems that don't support it
        Options ko = Options.builder(PhoneticSystem.korean).abbrev(AbbrevPolicy.INITIALS).build();
        assertThat(ko.sequence()).isFalse();
    }

    @Test
    void toneStrictMatchesNormalized() {
        Options strict = Options.builder(PhoneticSystem.mandarin).tone(TonePolicy.STRICT).build();
        assertThat(phonIn.matches("中", "zhong1", strict)).isTrue(); // normalized with tone digit
        assertThat(phonIn.matches("中", "zhong", strict)).isFalse(); // tone required -> not exact
        assertThat(phonIn.contains("中", "zhong", strict)).isTrue(); // but prefix still contains
    }

    //endregion
    //region shuangpin keyboard

    @Test
    void shuangpinMatchesTwoKeyCode() {
        // 中=zhong, 国=guo. zhong -> vs (flypy/zrm/mspy), vy (pyjj), as (abc); guo -> go everywhere.
        Options flypy = MandarinOptions.shuangpin("flypy");
        assertThat(phonIn.matches("中国", "vsgo", flypy)).isTrue();
        assertThat(phonIn.matches("中", "vs", flypy)).isTrue(); // single-char exact
        assertThat(phonIn.contains("中", "vs", flypy)).isTrue();
        assertThat(phonIn.contains("中", "zhong", flypy))
                .isFalse(); // quanpin surface is replaced by the code
        assertThat(phonIn.matches("中国", "vygo", flypy)).isFalse(); // pyjj code under flypy

        assertThat(phonIn.matches("中国", "vygo", MandarinOptions.shuangpin("pyjj"))).isTrue();
        assertThat(phonIn.matches("中国", "asgo", MandarinOptions.shuangpin("abc"))).isTrue();
        assertThat(phonIn.matches("中国", "vsgo", MandarinOptions.shuangpin("zrm"))).isTrue();

        // mspy maps finals onto non-letter keys ('ing' -> ';'); 'a' -> 'oa'. Matched verbatim.
        Options mspy = MandarinOptions.shuangpin("mspy");
        assertThat(phonIn.contains("并", "b;", mspy)).isTrue(); // 并 bing -> b;
        assertThat(phonIn.contains("阿", "oa", mspy)).isTrue(); // 阿 a -> oa
        assertThat(phonIn.contains("阿", "b;", mspy)).isFalse();

        // shuangpin is toneless: STRICT is a no-op (a code carries no tone digit).
        Options spStrict =
                MandarinOptions.shuangpin("flypy").toBuilder().tone(TonePolicy.STRICT).build();
        assertThat(phonIn.matches("中", "vs", spStrict)).isTrue();
        assertThat(phonIn.matches("中", "vs1", spStrict)).isFalse();

        // 首键简拼: under a non-identity keyboard the abbreviation is the first key of each code,
        // so 中国 (vs+go under flypy) matches "vg" but not "zg" (中's key is v, not z).
        Options spAbbrev =
                MandarinOptions.shuangpin("flypy").toBuilder()
                        .abbrev(AbbrevPolicy.INITIALS)
                        .build();
        assertThat(spAbbrev.sequence()).isTrue();
        assertThat(phonIn.contains("中国", "vg", spAbbrev)).isTrue();
        assertThat(phonIn.contains("中国", "zg", spAbbrev)).isFalse();

        // caching + identity
        assertThat(ShuangpinKeyboard.of("flypy")).isSameAs(ShuangpinKeyboard.of("FLYPY"));
        assertThat(Keyboard.identity.isIdentity()).isTrue();
        assertThat(flypy.keyboard().isIdentity()).isFalse();
        assertThat(flypy.keyboard()).isInstanceOf(ShuangpinKeyboard.class);
        assertThat(((ShuangpinKeyboard) flypy.keyboard()).scheme()).isEqualTo("flypy");
    }

    //endregion
    //region fuzzy rules

    @Test
    void fuzzySwapMakesCounterpartReachableBothDirections() {
        // A self-contained system: one char reads "zhen" (zh-initial), one reads "zen" (z-initial).
        PhoneticSystem s = new PhoneticSystem("TESTFUZZY", true, null, ToneConvention.DIGIT);
        s.putChar(new CharEntry('A', Collections.singletonList(new Reading(s, "zhen1"))));
        s.putChar(new CharEntry('B', Collections.singletonList(new Reading(s, "zen1"))));
        Options on = Options.builder(s).addFuzzy(FuzzyRules.fuzzyZhZ).build();

        // zh-initial: canonical "zhen" AND the z-variant "zen" both reachable.
        assertThat(phonIn.contains("A", "zhen", on)).isTrue();
        assertThat(phonIn.contains("A", "zen", on)).isTrue();
        // z-initial: symmetric -- the zh-variant "zhen" is reachable too.
        assertThat(phonIn.contains("B", "zen", on)).isTrue();
        assertThat(phonIn.contains("B", "zhen", on)).isTrue();

        // Without the rule, only the canonical surface matches.
        Options off = Options.builder(s).build();
        assertThat(phonIn.contains("A", "zen", off)).isFalse();
        assertThat(phonIn.contains("A", "zhen", off)).isTrue();
    }

    @Test
    void fuzzyRuleVariantsMatchSpec() {
        // SWAP rules are symmetric.
        assertThat(FuzzyRules.fuzzyZhZ.variants("zhen")).containsExactlyInAnyOrder("zen");
        assertThat(FuzzyRules.fuzzyZhZ.variants("zen")).containsExactlyInAnyOrder("zhen");
        assertThat(FuzzyRules.fuzzyZhZ.variants("a")).isEmpty();
        assertThat(FuzzyRules.fuzzyAngAn.variants("bang")).containsExactlyInAnyOrder("ban");
        assertThat(FuzzyRules.fuzzyUV.variants("nv")).containsExactlyInAnyOrder("nu");
        assertThat(FuzzyRules.fuzzyUV.variants("ju")).containsExactlyInAnyOrder("jv");
        assertThat(FuzzyRules.fuzzyUV.variants("gu")).isEmpty(); // 'u' after g is a real u
        // MERGE rules are one-way (user-side drop only).
        assertThat(FuzzyRules.fuzzyNgOmit.variants("ngo")).containsExactlyInAnyOrder("o");
        assertThat(FuzzyRules.fuzzyNgOmit.variants("o")).isEmpty();
        assertThat(FuzzyRules.fuzzyGwG.variants("gwo")).containsExactlyInAnyOrder("go");
        // Hepburn<->Kunrei: the 'hu' inside "chuu" must NOT match, but 'chu' at the start does.
        assertThat(FuzzyRules.fuzzyHepburnKunrei.variants("shi")).containsExactlyInAnyOrder("si");
        assertThat(FuzzyRules.fuzzyHepburnKunrei.variants("si")).containsExactlyInAnyOrder("shi");
        assertThat(FuzzyRules.fuzzyHepburnKunrei.variants("chuu"))
                .containsExactlyInAnyOrder("tyuu");
        assertThat(FuzzyRules.fuzzyHepburnKunrei.variants("hutu"))
                .containsExactlyInAnyOrder("futu", "hutsu"); // two clusters -> two variants
        assertThat(FuzzyRules.fuzzyLongVowel.variants("chuu")).containsExactlyInAnyOrder("chu");
    }

    @Test
    void fuzzyRulesRegisteredByName() {
        assertThat(FuzzyRules.rules()).hasSize(15);
        assertThat(FuzzyRules.byName("FUZZY_ZH_Z")).isSameAs(FuzzyRules.fuzzyZhZ);
        assertThat(FuzzyRules.byName("fuzzy_zh_z")) // exact UPPER_SNAKE id only
                .isNull();
        assertThat(FuzzyRules.byName("NOPE")).isNull();
        assertThat(FuzzyRules.fuzzyZhZ.toString()).isEqualTo("FUZZY_ZH_Z"); // name == dataset id
        // the mandarin char table tolerates a fuzzy option without exception
        Options o = Options.builder(PhoneticSystem.mandarin).addFuzzy(FuzzyRules.fuzzyZhZ).build();
        assertThat(phonIn.contains("中", "zong", o)).isTrue(); // 中 zhong -> zong variant
    }

    @Test
    void fuzzyAppliesToSyllableUnderShuangpin() {
        // 中=zhong. flypy: zhong->vs, zong->zs. Fuzzy must derive the variant from the toneless
        // SYLLABLE and then encode it, so "zs" (the z-variant's code) also matches.
        Options flypyFuzzy =
                MandarinOptions.shuangpin("flypy").toBuilder()
                        .addFuzzy(FuzzyRules.fuzzyZhZ)
                        .build();
        assertThat(phonIn.contains("中", "vs", flypyFuzzy)).isTrue(); // canonical code
        assertThat(phonIn.contains("中", "zs", flypyFuzzy)).isTrue(); // fuzzy variant's code
        // without the rule, only the canonical code matches
        assertThat(phonIn.contains("中", "zs", MandarinOptions.shuangpin("flypy"))).isFalse();
    }

    @Test
    void fuzzyIsInertUnderStrictTone() {
        // Fuzzy rules are a toneless-surface concept; under STRICT the surface is tone-bearing and
        // fuzzies are not applied, so "zong" does NOT match 中 (zhong1).
        Options strictFuzzy =
                Options.builder(PhoneticSystem.mandarin)
                        .tone(TonePolicy.STRICT)
                        .addFuzzy(FuzzyRules.fuzzyZhZ)
                        .build();
        assertThat(phonIn.contains("中", "zong", strictFuzzy)).isFalse(); // fuzzy inert under STRICT
        assertThat(phonIn.contains("中", "zhong1", strictFuzzy))
                .isTrue(); // toned primary still matches
        // contrast: under IGNORE (default) the fuzzy fires
        Options ignoreFuzzy =
                Options.builder(PhoneticSystem.mandarin).addFuzzy(FuzzyRules.fuzzyZhZ).build();
        assertThat(phonIn.contains("中", "zong", ignoreFuzzy)).isTrue();
    }

    //endregion
    //region IndexSet bitmap ops

    @Test
    void indexSetSetGetMerge() {
        IndexSet s = new IndexSet();
        assertThat(s.isEmpty()).isTrue();
        s.set(2);
        s.set(4);
        assertThat(s.get(2)).isTrue();
        assertThat(s.get(4)).isTrue();
        assertThat(s.get(3)).isFalse();
        assertThat(s.isEmpty()).isFalse();
    }

    @Test
    void indexSetMergeZeroReplacesOtherwiseUnions() {
        IndexSet zero = IndexSet.zero(); // {0}
        IndexSet other = new IndexSet();
        other.set(3);
        zero.merge(other); // merging into a bare {0} anchor replaces -> {3}
        assertThat(zero.get(0)).isFalse();
        assertThat(zero.get(3)).isTrue();

        IndexSet base = new IndexSet();
        base.set(2);
        base.merge(other); // not a bare anchor -> union -> {2,3}
        assertThat(base.get(2)).isTrue();
        assertThat(base.get(3)).isTrue();
    }

    @Test
    void indexSetTraverseAndForeach() {
        IndexSet s = new IndexSet();
        s.set(1);
        s.set(3);
        assertThat(s.traverse(i -> i == 3)).isTrue(); // short-circuits on first predicate-true
        assertThat(s.traverse(i -> i == 5)).isFalse();
        AtomicInteger sum = new AtomicInteger();
        s.foreach(sum::addAndGet); // 1 + 3
        assertThat(sum.get()).isEqualTo(4);
        assertThat(s.toString()).contains("1", "3");
    }

    @Test
    void indexSetStaticsAreDistinct() {
        assertThat(IndexSet.zero().get(0)).isTrue();
        assertThat(IndexSet.one().get(1)).isTrue();
        assertThat(IndexSet.none().isEmpty()).isTrue();
        assertThat(IndexSet.one().copy().get(1)).isTrue();
    }

    //endregion
    //region ToneConvention

    @Test
    void toneConventionStripsCorrectly() {
        assertThat(ToneConvention.DIGIT.strip("zhong1")).isEqualTo("zhong");
        assertThat(ToneConvention.DIGIT.strip("nei5")).isEqualTo("nei");
        assertThat(ToneConvention.DIGIT.strip("zhong")).isEqualTo("zhong"); // no digit
        assertThat(ToneConvention.DIGIT.strip("")).isEqualTo("");
        assertThat(ToneConvention.ZHUYIN.strip("ㄨˇ")).isEqualTo("ㄨ");
        assertThat(ToneConvention.ZHUYIN.strip("ㄓㄨㄥ")).isEqualTo("ㄓㄨㄥ"); // no mark (tone 1)
        assertThat(ToneConvention.ZHUYIN.strip("˙ㄉ")).isEqualTo("ㄉ"); // leading ˙
        assertThat(ToneConvention.NONE.strip("chuu")).isEqualTo("chuu");
    }

    //endregion
    //region PhoneticSystem & model holders

    @Test
    void phoneticSystemIdentity() {
        assertThat(new PhoneticSystem("MANDARIN")).isEqualTo(PhoneticSystem.mandarin);
        assertThat(PhoneticSystem.mandarin.abbreviable()).isTrue();
        assertThat(PhoneticSystem.japanese.abbreviable()).isFalse();
        assertThat(PhoneticSystem.mandarin.toneConvention()).isEqualTo(ToneConvention.DIGIT);
        assertThat(PhoneticSystem.zhuyin.toneConvention()).isEqualTo(ToneConvention.ZHUYIN);
    }

    @Test
    void customSystemIsIndependent() {
        PhoneticSystem hakka = new PhoneticSystem("HAKKA");
        assertThat(hakka.charEntry('A')).isNull();
        assertThat(hakka.charCount()).isZero();
        CharEntry entry = new CharEntry('A', Collections.singletonList(new Reading(hakka, "a")));
        hakka.putChar(entry);
        assertThat(hakka.charEntry('A')).isSameAs(entry);
        assertThat(hakka.charCount()).isEqualTo(1);
        hakka.clearData();
        assertThat(hakka.charCount()).isZero();
    }

    @Test
    void loadedDatasetHasMandarin() {
        assertThat(PhoneticSystem.mandarin.charEntry(0x4E2D)).isNotNull(); // 中
        assertThat(PhoneticSystem.mandarin.charCount()).isGreaterThan(10000);
        assertThat(phonIn.data()).isNotNull();
    }

    //endregion
    //region edge paths: empty text, literal chars, null guards

    @Test
    void emptyTextHandling() {
        Options o = Options.mandarinQuanpin();
        assertThat(phonIn.contains("", "", o)).isTrue(); // empty query matches empty text
        assertThat(phonIn.contains("", "x", o))
                .isFalse(); // non-empty query finds nothing in empty text
        assertThat(phonIn.matches("", "", o)).isTrue();
        assertThat(phonIn.begins("", "x", o)).isFalse();
    }

    @Test
    void literalCharMatchesItself() {
        Options o = Options.mandarinQuanpin();
        // 'a' has no Mandarin reading -> literal-only node; it matches the literal char 'a'.
        assertThat(phonIn.contains("a", "a", o)).isTrue();
        assertThat(phonIn.contains("a", "b", o)).isFalse();
        // a literal char inside text is matched via the char-lattice fallback
        assertThat(phonIn.contains("a中", "a", o)).isTrue();
    }

    @Test
    void nullGuardsDefaultToSafe() {
        PhoneticSystem s = new PhoneticSystem("CUSTOM", true, null, null);
        assertThat(s.toneConvention()).isEqualTo(ToneConvention.NONE);
        assertThat(s.defaultFuzzies()).isEmpty();
        CharEntry empty = new CharEntry('Z', Collections.<Reading>emptyList());
        assertThat(empty.system()).isNull(); // no readings -> null system
    }
    //endregion
}
