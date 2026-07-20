package dev.vfyjxf.phonin.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.vfyjxf.phonin.AbbrevPolicy;
import dev.vfyjxf.phonin.CodepointRouter;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.PolyphoneMode;
import dev.vfyjxf.phonin.PolyphoneTable;
import dev.vfyjxf.phonin.core.fuzzy.FuzzyRules;
import dev.vfyjxf.phonin.core.search.PreciseSearcher;
import dev.vfyjxf.phonin.data.PolyphoneTables;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link PolyphoneMode#PRECISE}: context-aware polyphone disambiguation driven by a {@link
 * PolyphoneTable}. Covers the core disambiguation cases, monotonic tightening (a polyphone not in
 * the table behaves as in OFF), composition with abbrev / fuzzy, the {@link
 * PolyphoneTable#load(InputStream)} TSV parser, the {@link Options} build validations, and the
 * accelerator/searcher rejection contract.
 */
class PolyphoneTest {

    private static PhonIn phonIn;
    private static PolyphoneTable table;

    @BeforeAll
    static void load() {
        phonIn = PhonIn.create(PhoneticSystem.mandarin);
        // 银行→yin2,hang2 ; 一行→yi1,xing2 ; 重要→zhong4,yao4 ; 重新→chong2,xin1
        table =
                PolyphoneTable.builder(PhoneticSystem.mandarin)
                        .add("银行", "yin2", "hang2")
                        .add("一行", "yi1", "xing2")
                        .add("重要", "zhong4", "yao4")
                        .add("重新", "chong2", "xin1")
                        .build();
    }

    private static Options precise(PolyphoneTable t) {
        return Options.builder(PhoneticSystem.mandarin).polyphone(PolyphoneMode.PRECISE, t).build();
    }

    //region core disambiguation

    @Test
    void bankForcesHangNotXing() {
        Options off = Options.mandarinQuanpin();
        Options on = precise(table);
        assertThat(phonIn.contains("银行", "yinxing", off)).isTrue(); // today: any reading
        assertThat(phonIn.contains("银行", "yinxing", on)).isFalse(); // 行 forced to hang
        assertThat(phonIn.contains("银行", "yinhang", on)).isTrue();
    }

    @Test
    void rowForcesXingNotHang() {
        Options on = precise(table);
        assertThat(phonIn.contains("一行", "yixing", on)).isTrue(); // 行 forced to xing
        assertThat(phonIn.contains("一行", "yihang", on)).isFalse();
    }

    @Test
    void sameCharDifferentReadingsInOneText() {
        Options on = precise(table);
        // 银行一行: 行@1 = hang, 行@3 = xing
        assertThat(phonIn.contains("银行一行", "yinhangyixing", on)).isTrue();
        assertThat(phonIn.contains("银行一行", "yinxingyihang", on)).isFalse();
    }

    @Test
    void importantAndRestartDisambiguateZhong() {
        Options on = precise(table);
        // 重 in 重要 = zhong4; in 重新 = chong2
        assertThat(phonIn.contains("重要", "zhongyao", on)).isTrue();
        assertThat(phonIn.contains("重要", "chongyao", on)).isFalse();
        assertThat(phonIn.contains("重新", "chongxin", on)).isTrue();
        assertThat(phonIn.contains("重新", "zhongxin", on)).isFalse();
    }

    //endregion
    //region monotonic tightening

    @Test
    void polyphoneNotInTableBehavesAsOff() {
        Options off = Options.mandarinQuanpin();
        Options on = precise(table);
        // 了 (le/liao3/liao4) is a polyphone absent from the table -> identical in both modes
        assertThat(phonIn.contains("了", "liao", on)).isEqualTo(phonIn.contains("了", "liao", off));
        assertThat(phonIn.contains("了", "le", on)).isEqualTo(phonIn.contains("了", "le", off));
        assertThat(phonIn.contains("了", "liao", on)).isTrue();
    }

    @Test
    void offModeIsZeroBehaviourChange() {
        Options off = Options.mandarinQuanpin();
        Options offWithTable =
                Options.builder(PhoneticSystem.mandarin)
                        .polyphone(PolyphoneMode.OFF, table)
                        .build();
        // OFF never consults the table; results identical with or without one
        String[][] cases = {
            {"银行", "yinxing"}, {"银行", "yinhang"}, {"一行", "yixing"}, {"重要", "zhongyao"}
        };
        for (String[] c : cases) {
            assertThat(phonIn.contains(c[0], c[1], offWithTable))
                    .as("OFF+table contains('%s','%s')", c[0], c[1])
                    .isEqualTo(phonIn.contains(c[0], c[1], off));
        }
    }

    //endregion
    //region composition with abbrev / fuzzy

    @Test
    void preciseComposesWithAbbrevInitials() {
        Options on =
                Options.builder(PhoneticSystem.mandarin)
                        .polyphone(PolyphoneMode.PRECISE, table)
                        .abbrev(AbbrevPolicy.INITIALS)
                        .build();
        // 银行 forced readings: yin + hang -> initials yh (not yx)
        assertThat(phonIn.contains("银行", "yh", on)).isTrue();
        assertThat(phonIn.contains("银行", "yx", on)).isFalse();
        // 一行 forced: yi + xing -> initials yx
        assertThat(phonIn.contains("一行", "yx", on)).isTrue();
        assertThat(phonIn.contains("一行", "yh", on)).isFalse();
    }

    @Test
    void preciseComposesWithFuzzyZhZ() {
        Options on =
                Options.builder(PhoneticSystem.mandarin)
                        .polyphone(PolyphoneMode.PRECISE, table)
                        .addFuzzy(FuzzyRules.fuzzyZhZ)
                        .build();
        // 重 in 重要 forced to zhong4; FUZZY_ZH_Z gives a zong variant -> "zongyao" matches
        assertThat(phonIn.contains("重要", "zongyao", on)).isTrue();
        // without fuzzy the same query fails
        assertThat(phonIn.contains("重要", "zongyao", precise(table))).isFalse();
    }

    //endregion
    //region begins / matches

    @Test
    void preciseWorksOnBeginsAndMatches() {
        Options on = precise(table);
        assertThat(phonIn.begins("银行", "yinhang", on)).isTrue();
        assertThat(phonIn.begins("银行", "yinxing", on)).isFalse();
        assertThat(phonIn.matches("银行", "yinhang", on)).isTrue();
        assertThat(phonIn.matches("银行", "yinxing", on)).isFalse();
    }

    //endregion
    //region TSV loader

    @Test
    void loadInputStreamParsesSampleTsv() throws IOException {
        PolyphoneTable loaded;
        try (InputStream in = sampleTsv()) {
            loaded = PolyphoneTables.load(in);
        }
        Options on = precise(loaded);
        // Reproduces the builder cases
        assertThat(phonIn.contains("银行", "yinxing", on)).isFalse();
        assertThat(phonIn.contains("银行", "yinhang", on)).isTrue();
        assertThat(phonIn.contains("一行", "yixing", on)).isTrue();
        assertThat(phonIn.contains("一行", "yihang", on)).isFalse();
        assertThat(phonIn.contains("重要", "zhongyao", on)).isTrue();
        assertThat(phonIn.contains("重新", "chongxin", on)).isTrue();
    }

    @Test
    void loadFromResourceWorks() throws IOException {
        // The committed sample fixture lives in phonin-core test resources; it is on the test
        // classpath.
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = PolyphoneTest.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream("phonin/polyphone/sample-mandarin.tsv")) {
            assertThat(in).as("sample-mandarin.tsv on classpath").isNotNull();
            PolyphoneTable loaded = PolyphoneTables.load(in);
            Options on = precise(loaded);
            assertThat(phonIn.contains("银行", "yinhang", on)).isTrue();
            assertThat(phonIn.contains("银行", "yinxing", on)).isFalse();
        }
    }

    @Test
    void loadSkipsCommentsBlanksAndBadRows() throws IOException {
        String tsv =
                "# comment line\n"
                        + "\n"
                        + "U+94F6,U+884C\t银行\tyin2,hang2\tMOONBILSTM\t0.99\n"
                        + "garbage line with no tabs\n"
                        + "U+4E00,U+884C\t一行\tyi1,xing2\n" // no optional columns
                        + "U+91CD\t重\tzhong4,yao4\n"; // readings count != codepoint count ->
        // skipped
        try (InputStream in = new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8))) {
            PolyphoneTable loaded = PolyphoneTables.load(in);
            Options on = precise(loaded);
            assertThat(phonIn.contains("银行", "yinhang", on)).isTrue();
            assertThat(phonIn.contains("一行", "yixing", on)).isTrue();
            // 重 alone (no 重要 word) was skipped -> 重 stays a free polyphone
            assertThat(phonIn.contains("重", "zhong", on)).isTrue();
            assertThat(phonIn.contains("重", "chong", on)).isTrue();
        }
    }

    @Test
    void builderRejectsMismatchedReadingsCount() {
        assertThatThrownBy(
                        () ->
                                PolyphoneTable.builder(PhoneticSystem.mandarin)
                                        .add("银行", "yin2")
                                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    //endregion
    //region Options build validation

    @Test
    void preciseWithoutTableIsRejected() {
        assertThatThrownBy(
                        () ->
                                Options.builder(PhoneticSystem.mandarin)
                                        .polyphoneMode(PolyphoneMode.PRECISE)
                                        .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void preciseWithRouterIsRejected() {
        assertThatThrownBy(
                        () ->
                                Options.multi(
                                                new CodepointRouter() {
                                                    @Override
                                                    public PhoneticSystem systemFor(int codepoint) {
                                                        return PhoneticSystem.mandarin;
                                                    }
                                                })
                                        .toBuilder()
                                        .polyphone(PolyphoneMode.PRECISE, table)
                                        .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toBuilderPreservesPolyphoneSettings() {
        Options on = precise(table);
        Options rebuilt = on.toBuilder().build();
        assertThat(rebuilt.polyphoneMode()).isEqualTo(PolyphoneMode.PRECISE);
        assertThat(rebuilt.polyphoneTable()).isSameAs(table);
        assertThat(phonIn.contains("银行", "yinxing", rebuilt)).isFalse();
    }

    //endregion
    //region accelerator / searcher contract

    @Test
    void compileRejectsPrecise() {
        Options on = precise(table);
        assertThatThrownBy(() -> phonIn.compile("yinhang", on))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precise polyphone mode");
    }

    @Test
    void searcherAcceptsPreciseViaPreciseSearcher() {
        Options on = precise(table);
        // searcher() returns a PreciseSearcher (trie-based, PRECISE-capable) for PRECISE mode.
        Searcher<String> s = phonIn.searcher(SearcherLogic.CONTAIN, on);
        assertThat(s).isInstanceOf(PreciseSearcher.class);
        s.put("银行", "bank");
        s.put("一行", "row");
        assertThat(s.search("yinhang")).containsExactly("bank");
        assertThat(s.search("yixing")).containsExactly("row");
        assertThat(s.search("yihang")).isEmpty(); // 行 forced to xing in 一行
    }

    @Test
    void directApiAcceptsPrecise() {
        Options on = precise(table);
        // already exercised above; restate the contract: no exception, real result
        assertThat(phonIn.contains("银行", "yinhang", on)).isTrue();
        assertThat(phonIn.begins("银行", "yinhang", on)).isTrue();
        assertThat(phonIn.matches("银行", "yinhang", on)).isTrue();
    }

    //endregion
    //region helpers

    /**
     * The same 4-word content the builder table uses, as a TSV byte stream.
     */
    private static InputStream sampleTsv() {
        String tsv =
                "# PhonIn polyphone table — sample\n"
                        + "U+94F6,U+884C\t银行\tyin2,hang2\tMOONBILSTM\t0.99\n"
                        + "U+4E00,U+884C\t一行\tyi1,xing2\tMOONBILSTM\t0.97\n"
                        + "U+91CD,U+8981\t重要\tzhong4,yao4\tMOONBILSTM\t0.99\n"
                        + "U+91CD,U+65B0\t重新\tchong2,xin1\tMOONBILSTM\t0.98\n";
        return new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8));
    }
    //endregion
}
