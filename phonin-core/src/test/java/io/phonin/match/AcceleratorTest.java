package io.phonin.match;

import static org.assertj.core.api.Assertions.assertThat;

import io.phonin.AcceleratedQuery;
import io.phonin.Options;
import io.phonin.PhonIn;
import io.phonin.PhoneticSystem;
import io.phonin.SystemOptions;
import io.phonin.fuzzy.FuzzyRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The accelerator is an optimization, not a semantic change: every {@link
 * io.phonin.AcceleratedQuery} result must equal the equivalent direct {@link PhonIn} call for the
 * same {@link Options}. This pins that invariant across modes, systems, fuzzy, and shuangpin.
 */
class AcceleratorTest {

    private static PhonIn phonIn;

    @BeforeAll
    static void load() {
        phonIn = PhonIn.create(PhoneticSystem.mandarin);
    }

    @Test
    void compiledQueryEqualsDirectMatcherAcrossModes() {
        Options o = Options.mandarinQuanpin();
        String[][] cases = {
            {"中国", "zhongguo"}, // whole word
            {"中国", "zhong"}, // prefix of first syllable
            {"中国", "guo"}, // second syllable
            {"中", "zho"}, // partial syllable (prefix)
            {"中", "zhong"}, // whole syllable
            {"美国", "mei"}, // first syllable
            {"美国", "guo"}, // second syllable
            {"中国", "zong"}, // not a match without fuzzy (zhong != zong)
            {"中国", ""}, // empty query
        };
        for (String[] c : cases) {
            String text = c[0];
            String query = c[1];
            AcceleratedQuery q = phonIn.compile(query, o);
            assertThat(q.contains(text))
                    .as("contains('%s','%s')", text, query)
                    .isEqualTo(phonIn.contains(text, query, o));
            assertThat(q.begins(text))
                    .as("begins('%s','%s')", text, query)
                    .isEqualTo(phonIn.begins(text, query, o));
            assertThat(q.matches(text))
                    .as("matches('%s','%s')", text, query)
                    .isEqualTo(phonIn.matches(text, query, o));
        }
    }

    @Test
    void compiledQueryHonorsFuzzy() {
        Options fuzzy =
                Options.builder(PhoneticSystem.mandarin).addFuzzy(FuzzyRules.fuzzyZhZ).build();
        AcceleratedQuery q = phonIn.compile("zong", fuzzy); // 中 zhong -> zong variant
        assertThat(q.contains("中")).isTrue();
        assertThat(q.contains("中")).isEqualTo(phonIn.contains("中", "zong", fuzzy));
    }

    @Test
    void compiledQueryHonorsShuangpin() {
        Options sp = SystemOptions.mandarinShuangpin("flypy");
        AcceleratedQuery q = phonIn.compile("vsgo", sp); // 中(vs) 国(go)
        assertThat(q.matches("中国")).isTrue();
        assertThat(q.matches("中国")).isEqualTo(phonIn.matches("中国", "vsgo", sp));
    }

    @Test
    void compiledQueryAcrossOtherSystems() {
        // Japanese: 食べる = taberu (Hepburn). Verify compile agrees with direct.
        Options o = Options.japaneseRomaji();
        AcceleratedQuery q = phonIn.compile("tabe", o);
        assertThat(q.contains("食べる")).isEqualTo(phonIn.contains("食べる", "tabe", o));
        assertThat(q.begins("食べる")).isEqualTo(phonIn.begins("食べる", "tabe", o));
    }
}
