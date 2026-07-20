package dev.vfyjxf.phonin.core.search;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vfyjxf.phonin.AbbrevPolicy;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.ToneConvention;
import dev.vfyjxf.phonin.core.PhonIn;
import dev.vfyjxf.phonin.core.fuzzy.FuzzyRules;
import dev.vfyjxf.phonin.mandarin.MandarinOptions;
import dev.vfyjxf.phonin.model.CharEntry;
import dev.vfyjxf.phonin.model.Reading;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A {@link Searcher} is an optimization, not a semantic change: its results must equal the direct
 * {@link dev.vfyjxf.phonin.Matcher} over the same names + {@link Options} + {@link SearcherLogic}.
 * Both {@link SimpleSearcher} and {@link TreeSearcher} are held to this invariant, and to each
 * other, across logics, systems, fuzzy, shuangpin, and abbreviation — and at index sizes large
 * enough to force the trie's {@code NDense}→{@code NSlice}→{@code NMap} self-tuning.
 */
class SearcherTest {

    private static PhonIn phonIn;

    @BeforeAll
    static void load() {
        phonIn = PhonIn.create(PhoneticSystem.mandarin);
    }

    private static final List<String> NAMES = new ArrayList<>();

    static {
        // 中=zhong 国=guo 华=hua 心=xin 美=mei 山=shan 水=shui 王=wang
        for (String w : new String[] {"中国", "中华", "中心", "美国", "山水", "国王"}) {
            NAMES.add(w);
        }
    }

    @Test
    void containMatchesDirect() {
        Options o = Options.mandarinQuanpin();
        assertAllLogics(NAMES, o, new String[] {"zhong", "guo", "hua", "wang", "sh", "zong", ""});
    }

    @Test
    void preciseContain() {
        SimpleSearcher<String> s = index(NAMES, SearcherLogic.CONTAIN, Options.mandarinQuanpin());
        assertThat(s.search("zhong")).containsExactlyInAnyOrder("中国", "中华", "中心");
        assertThat(s.search("guo")).containsExactlyInAnyOrder("中国", "美国", "国王");
        assertThat(s.search("hua")).containsExactlyInAnyOrder("中华");
        assertThat(s.search("wang")).containsExactlyInAnyOrder("国王");
    }

    @Test
    void beginMatchesDirect() {
        Options o = Options.mandarinQuanpin();
        // BEGIN: query must match from the start of a name.
        SimpleSearcher<String> s = index(NAMES, SearcherLogic.BEGIN, o);
        assertThat(s.search("zhong")).containsExactlyInAnyOrder("中国", "中华", "中心");
        assertThat(s.search("guo")).containsExactlyInAnyOrder("国王"); // 中国/美国 do not begin with 国
        assertThat(new TreeSet<>(s.search("zhong")))
                .isEqualTo(direct(NAMES, "zhong", o, SearcherLogic.BEGIN));
    }

    @Test
    void equalMatchesDirect() {
        Options o = Options.mandarinQuanpin();
        SimpleSearcher<String> s = index(NAMES, SearcherLogic.EQUAL, o);
        assertThat(s.search("zhongguo")).containsExactlyInAnyOrder("中国");
        assertThat(s.search("zhonghua")).containsExactlyInAnyOrder("中华");
        assertThat(s.search("zhong")).isEmpty(); // not a whole-word match
    }

    @Test
    void fuzzyAndShuangpinAndAbbrev() {
        Options fuzzy =
                Options.builder(PhoneticSystem.mandarin).addFuzzy(FuzzyRules.fuzzyZhZ).build();
        assertBothEqualDirect(NAMES, fuzzy, SearcherLogic.CONTAIN, new String[] {"zong", "zhong"});

        Options sp = MandarinOptions.shuangpin("flypy");
        assertBothEqualDirect(NAMES, sp, SearcherLogic.CONTAIN, new String[] {"vs", "go"});

        Options abbrev =
                Options.builder(PhoneticSystem.mandarin).abbrev(AbbrevPolicy.INITIALS).build();
        // 简拼 = full pinyin initial per syllable (中=zh, 山=sh, 国=g, 美=m, …): zh/ch/sh stay 2 chars,
        // so they don't collide with z/c/s initials. Tree/Simple still == direct matcher.
        assertBothEqualDirect(
                NAMES, abbrev, SearcherLogic.CONTAIN, new String[] {"zhg", "shs", "mg", "gw"});
        // Tightened (中 needs "zh", not bare 'z') + fuzzy composition (z/zh is the fuzzy layer):
        assertThat(phonIn.contains("中国", "zhg", abbrev)).isTrue();
        assertThat(phonIn.contains("中国", "zg", abbrev)).isFalse();
        Options abbrevFuzzy =
                Options.builder(PhoneticSystem.mandarin)
                        .abbrev(AbbrevPolicy.INITIALS)
                        .addFuzzy(FuzzyRules.fuzzyZhZ)
                        .build();
        assertThat(phonIn.contains("中国", "zg", abbrevFuzzy)).isTrue();
    }

    @Test
    void largeIndexInvariantForcesTrieSelfTuning() {
        // The engine matches words through their constituent chars, so PhoneticSystem.words() is
        // not populated; synthesize a large set of multi-char names from a small char alphabet so
        // many names share prefixes/suffixes — this forces the trie's NDense→NSlice→NMap splits.
        // The invariant (searcher == direct matcher) is system-agnostic, so synthetic names are a
        // valid oracle.
        List<String> names = syntheticNames(4000);
        assertThat(names.size()).as("indexed enough names to stress the trie").isGreaterThan(500);

        Options o = Options.mandarinQuanpin();
        // a spread of queries: common initials, full syllables, partials, and a non-match
        assertBothEqualDirect(
                names,
                o,
                SearcherLogic.CONTAIN,
                new String[] {"zhong", "guo", "da", "a", "z", "qqqx"});
        assertBothEqualDirect(names, o, SearcherLogic.BEGIN, new String[] {"zhong", "a", "da"});
        assertBothEqualDirect(names, o, SearcherLogic.EQUAL, new String[] {"zhongguo", "a"});
    }

    /**
     * Generate {@code count} distinct 4-char names over a small alphabet. Uses a base-N encoding of
     * the counter (most-significant digit first) so consecutive names share long prefixes — this
     * forces the trie to build deep, shared subtrees (exercising {@code NSlice}/{@code NMap}).
     */
    @Test
    void supplementaryCodepointNameIndexesCorrectly() {
        // The whole point of the int-codepoint port (vs PinIn's char-based searchers): a
        // supplementary-plane CJK char (a surrogate pair in Java's UTF-16) must index and match as
        // ONE trie element, not two surrogate halves.
        PhoneticSystem s = new PhoneticSystem("SUPP", true, null, ToneConvention.DIGIT);
        int cp = 0x2A6A5; // 𪚥 (CJK Ext B; surrogate pair in UTF-16)
        s.putChar(new CharEntry(cp, Collections.singletonList(new Reading(s, "long4"))));
        Options o = Options.builder(s).build();
        String name = new String(Character.toChars(cp));
        assertThat(name.length()).isEqualTo(2); // two UTF-16 units, one codepoint
        assertThat(name.codePointCount(0, name.length())).isEqualTo(1);

        List<Searcher<String>> searchers = new ArrayList<>();
        searchers.add(new SimpleSearcher<>(SearcherLogic.CONTAIN, o));
        searchers.add(new TreeSearcher<>(SearcherLogic.CONTAIN, o));
        for (Searcher<String> sr : searchers) {
            sr.put(name, name);
            assertThat(sr.search("long")).containsExactlyInAnyOrder(name);
            assertThat(sr.search("lo")).containsExactlyInAnyOrder(name); // partial prefix
            assertThat(sr.search("x")).isEmpty();
        }
    }

    @Test
    void supplementaryAlphabetStressesSplitTrie() {
        // Force the trie past its 128-entry split threshold using ONLY supplementary-plane
        // codepoints
        // (CJK Ext B) as trie keys, locking the int-codepoint port's correctness at scale (PinIn's
        // char-based searchers could not represent these at all).
        PhoneticSystem s = new PhoneticSystem("SUPP2", true, null, ToneConvention.NONE);
        int[] cps = {0x20000, 0x20001, 0x20002, 0x20003, 0x20004, 0x20005, 0x20006, 0x20007};
        for (int i = 0; i < cps.length; i++) {
            String syll = "s" + i;
            s.putChar(new CharEntry(cps[i], Collections.singletonList(new Reading(s, syll))));
        }
        Options o = Options.builder(s).build();

        List<String> names = new ArrayList<>();
        for (int i = 0; i < 400; i++) { // > THRESHOLD (128) -> forces NDense→NSlice→NMap
            int n = i;
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 3; j++) {
                sb.appendCodePoint(cps[n % cps.length]);
                n /= cps.length;
            }
            names.add(sb.toString());
        }
        assertThat(names.size()).isGreaterThan(128);

        assertBothEqualDirect(
                names, o, SearcherLogic.CONTAIN, new String[] {"s0", "s1", "s7", "x"});
        assertBothEqualDirect(names, o, SearcherLogic.BEGIN, new String[] {"s0", "s7"});
        assertBothEqualDirect(names, o, SearcherLogic.EQUAL, new String[] {"s0s0s0"});
    }

    @Test
    void emptyAndSingleCharNames() {
        Options o = Options.mandarinQuanpin();
        List<String> names = new ArrayList<>();
        names.add("中"); // single codepoint
        names.add(""); // empty name
        names.add("中国");
        // empty query matches every non-empty name for CONTAIN/BEGIN; EQUAL only the empty name.
        assertBothEqualDirect(names, o, SearcherLogic.CONTAIN, new String[] {"", "zhong", "guo"});
        assertBothEqualDirect(names, o, SearcherLogic.EQUAL, new String[] {"", "zhongguo"});
    }

    private static List<String> syntheticNames(int count) {
        String alphabet = "中国大会人民上下"; // 8 common chars, all with Mandarin readings
        int base = alphabet.length();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (int i = 0; i < count && i < base * base * base * base; i++) {
            int n = i;
            char[] digits = new char[4];
            for (int j = 0; j < 4; j++) {
                digits[j] = alphabet.charAt(n % base);
                n /= base;
            }
            StringBuilder sb = new StringBuilder();
            for (int j = 3; j >= 0; j--) sb.append(digits[j]); // MSB first => shared prefixes
            set.add(sb.toString());
        }
        return new ArrayList<>(set);
    }

    //region helpers

    private void assertAllLogics(List<String> names, Options o, String[] queries) {
        for (SearcherLogic logic : SearcherLogic.values()) {
            assertBothEqualDirect(names, o, logic, queries);
        }
    }

    private void assertBothEqualDirect(
            List<String> names, Options o, SearcherLogic logic, String[] queries) {
        SimpleSearcher<String> simple = index(names, logic, o);
        TreeSearcher<String> tree = new TreeSearcher<>(logic, o);
        for (String n : names) tree.put(n, n);
        for (String q : queries) {
            TreeSet<String> expected = direct(names, q, o, logic);
            assertThat(new TreeSet<>(simple.search(q)))
                    .as("Simple %s '%s'", logic, q)
                    .isEqualTo(expected);
            assertThat(new TreeSet<>(tree.search(q)))
                    .as("Tree %s '%s'", logic, q)
                    .isEqualTo(expected);
        }
    }

    private static SimpleSearcher<String> index(
            List<String> names, SearcherLogic logic, Options o) {
        SimpleSearcher<String> s = new SimpleSearcher<>(logic, o);
        for (String n : names) s.put(n, n);
        return s;
    }

    private static TreeSet<String> direct(
            List<String> names, String query, Options o, SearcherLogic logic) {
        TreeSet<String> ret = new TreeSet<>();
        for (String n : names) {
            boolean m;
            switch (logic) {
                case CONTAIN:
                    m = phonIn.contains(n, query, o);
                    break;
                case BEGIN:
                    m = phonIn.begins(n, query, o);
                    break;
                case EQUAL:
                    m = phonIn.matches(n, query, o);
                    break;
                default:
                    throw new IllegalStateException("logic: " + logic);
            }
            if (m) ret.add(n);
        }
        return ret;
    }
    //endregion
}
