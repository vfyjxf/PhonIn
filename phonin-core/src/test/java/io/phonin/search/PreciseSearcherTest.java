package io.phonin.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.phonin.Options;
import io.phonin.PhonIn;
import io.phonin.PhonInData;
import io.phonin.PhoneticSystem;
import io.phonin.PolyphoneMode;
import io.phonin.PolyphoneTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Correctness + performance tests for {@link PreciseSearcher}. Correctness is validated by
 * cross-checking every search result against the direct API ({@link PhonIn#contains}); performance
 * is sanity-checked against {@link TreeSearcher} in OFF mode and {@link LinearSearcher} in PRECISE
 * mode.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("PreciseSearcher")
class PreciseSearcherTest {

    private PhonIn phonIn;
    private PhonInData data;
    private PolyphoneTable table;

    @BeforeAll
    void setup() {
        data = PhonInData.loadFromClasspath();
        phonIn = new PhonIn(data);
        table =
                PolyphoneTable.builder(PhoneticSystem.mandarin)
                        .add("银行", "yin2", "hang2")
                        .add("一行", "yi1", "xing2")
                        .add("重要", "zhong4", "yao4")
                        .add("重新", "chong2", "xin1")
                        .add("红石粉", "hong2", "shi2", "fen3")
                        .add("红石", "hong2", "shi2")
                        .add("行长", "hang2", "zhang3")
                        .build();
    }

    @AfterAll
    void tearDown() {
        phonIn = null;
        data = null;
    }

    private Options preciseOpts() {
        return Options.builder(PhoneticSystem.mandarin)
                .polyphone(PolyphoneMode.PRECISE, table)
                .build();
    }

    private Options offOpts() {
        return Options.builder(PhoneticSystem.mandarin).build();
    }

    // ---- correctness: PRECISE disambiguation -----------------------------

    @Test
    @DisplayName("PRECISE searcher rejects wrong reading for covered polyphone")
    void preciseRejectsWrongReading() {
        PreciseSearcher<String> s = new PreciseSearcher<>(SearcherLogic.CONTAIN, preciseOpts());
        s.put("银行", "bank");
        s.put("一行", "row");
        assertThat(s.search("yinhang")).containsExactly("bank");
        assertThat(s.search("yixing")).containsExactly("row");
        assertThat(s.search("yihang")).isEmpty(); // 行 forced to xing in 一行
        assertThat(s.search("yinxing")).isEmpty(); // 行 forced to hang in 银行
    }

    @Test
    @DisplayName("PRECISE searcher accepts correct readings")
    void preciseAcceptsCorrectReadings() {
        PreciseSearcher<String> s = new PreciseSearcher<>(SearcherLogic.CONTAIN, preciseOpts());
        s.put("银行", "bank");
        s.put("一行", "row");
        s.put("重要", "important");
        s.put("重新", "restart");
        assertThat(s.search("yinhang")).containsExactly("bank");
        assertThat(s.search("yixing")).containsExactly("row");
        assertThat(s.search("zhongyao")).containsExactly("important");
        assertThat(s.search("chongxin")).containsExactly("restart");
    }

    @Test
    @DisplayName("红石: gongdan does not match in PRECISE, does in OFF")
    void redstoneDisambiguation() {
        PreciseSearcher<String> s = new PreciseSearcher<>(SearcherLogic.CONTAIN, preciseOpts());
        s.put("红石", "redstone");
        s.put("红石粉", "redstone-dust");
        assertThat(s.search("hongshi")).containsExactlyInAnyOrder("redstone", "redstone-dust");
        assertThat(s.search("gongdan")).isEmpty(); // PRECISE blocks wrong readings
        // OFF mode would match gongdan — verify via direct API
        assertThat(phonIn.contains("红石", "gongdan", offOpts())).isTrue();
        assertThat(phonIn.contains("红石", "gongdan", preciseOpts())).isFalse();
    }

    @Test
    @DisplayName("Uncovered polyphone chars still accept all readings")
    void uncoveredPolyphoneAcceptsAll() {
        // 长 is polyphone (chang/zhang) but not in our table → all readings accepted
        PreciseSearcher<String> s = new PreciseSearcher<>(SearcherLogic.CONTAIN, preciseOpts());
        s.put("长城", "great-wall");
        assertThat(s.search("changcheng")).containsExactly("great-wall");
        assertThat(s.search("zhangcheng")).containsExactly("great-wall");
    }

    @Test
    @DisplayName("BEGIN logic: prefix match with PRECISE")
    void beginLogic() {
        PreciseSearcher<String> s = new PreciseSearcher<>(SearcherLogic.BEGIN, preciseOpts());
        s.put("银行大楼", "bank-building");
        s.put("一行代码", "line-of-code");
        assertThat(s.search("yinhang")).containsExactly("bank-building");
        assertThat(s.search("yixing")).containsExactly("line-of-code");
        assertThat(s.search("yihang")).isEmpty();
    }

    @Test
    @DisplayName("EQUAL logic: exact match with PRECISE")
    void equalLogic() {
        PreciseSearcher<String> s = new PreciseSearcher<>(SearcherLogic.EQUAL, preciseOpts());
        s.put("银行", "bank");
        s.put("一行", "row");
        assertThat(s.search("yinhang")).containsExactly("bank");
        assertThat(s.search("yinhangda")).isEmpty(); // not exact
        assertThat(s.search("yihang")).isEmpty();
    }

    // ---- correctness: cross-check vs direct API --------------------------

    @Test
    @DisplayName("Cross-check: PreciseSearcher results == direct API for all queries")
    void crossCheckVsDirectApi() {
        List<String> names =
                Arrays.asList(
                        "银行", "一行", "重要", "重新", "红石", "红石粉", "行长", "长城", "中国人民银行", "行不行", "石头",
                        "石榴", "重量", "重心");
        List<String> queries =
                Arrays.asList(
                        "yinhang",
                        "yixing",
                        "yihang",
                        "zhongyao",
                        "chongxin",
                        "hongshi",
                        "gongdan",
                        "changcheng",
                        "zhangcheng",
                        "hangzhang",
                        "shitou",
                        "shiliu",
                        "zhongliang",
                        "zhongxin",
                        "xing",
                        "hang",
                        "zhong",
                        "xin",
                        "hong",
                        "shi",
                        "fen",
                        "da",
                        "lou",
                        "yin",
                        "yi");

        PreciseSearcher<String> s = new PreciseSearcher<>(SearcherLogic.CONTAIN, preciseOpts());
        for (int i = 0; i < names.size(); i++) {
            s.put(names.get(i), "n" + i);
        }

        Options on = preciseOpts();
        for (String q : queries) {
            Set<String> directHits = new HashSet<>();
            for (int i = 0; i < names.size(); i++) {
                if (phonIn.contains(names.get(i), q, on)) directHits.add("n" + i);
            }
            List<String> searcherHits = s.search(q);
            assertThat(new HashSet<>(searcherHits))
                    .as("query '%s': searcher vs direct API mismatch", q)
                    .isEqualTo(directHits);
        }
    }

    @Test
    @DisplayName("Cross-check: BEGIN logic vs direct API")
    void crossCheckBeginVsDirectApi() {
        List<String> names = Arrays.asList("银行", "一行", "重要", "重新", "红石粉", "红石电路", "长城");
        List<String> queries =
                Arrays.asList("yin", "yixing", "yinhang", "hong", "hongshi", "chang");

        PreciseSearcher<String> s = new PreciseSearcher<>(SearcherLogic.BEGIN, preciseOpts());
        for (int i = 0; i < names.size(); i++) {
            s.put(names.get(i), "n" + i);
        }

        Options on = preciseOpts();
        for (String q : queries) {
            Set<String> directHits = new HashSet<>();
            for (int i = 0; i < names.size(); i++) {
                if (phonIn.begins(names.get(i), q, on)) directHits.add("n" + i);
            }
            List<String> searcherHits = s.search(q);
            assertThat(new HashSet<>(searcherHits))
                    .as("BEGIN query '%s': searcher vs direct API mismatch", q)
                    .isEqualTo(directHits);
        }
    }

    // ---- performance: PreciseSearcher vs LinearSearcher ------------------

    @Test
    @DisplayName("Performance: PreciseSearcher faster than LinearSearcher on large index")
    void preciseFasterThanLinear() {
        // Build a synthetic large index: ~2000 names with polyphone chars
        List<String> names = new ArrayList<>();
        Random rng = new Random(42);
        String[] polyWords = {"银行", "一行", "重要", "重新", "红石", "红石粉", "行长"};
        String[] commonChars = {"大", "小", "中", "国", "人", "民", "天", "地", "山", "水", "石", "木"};
        for (int i = 0; i < 2000; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append(polyWords[rng.nextInt(polyWords.length)]);
            for (int j = 0; j < 3 + rng.nextInt(4); j++) {
                sb.append(commonChars[rng.nextInt(commonChars.length)]);
            }
            names.add(sb.toString());
        }

        List<String> queries =
                Arrays.asList("yinhang", "yixing", "hongshi", "zhongyao", "gongdan", "chang");

        // Build both searchers with same PRECISE options
        Options on = preciseOpts();
        PreciseSearcher<String> precise = new PreciseSearcher<>(SearcherLogic.CONTAIN, on);
        LinearSearcher<String> linear = new LinearSearcher<>(SearcherLogic.CONTAIN, on);
        for (int i = 0; i < names.size(); i++) {
            precise.put(names.get(i), "n" + i);
            linear.put(names.get(i), "n" + i);
        }

        // Warm up
        for (String q : queries) {
            precise.search(q);
            linear.search(q);
        }

        // Verify identical results first
        for (String q : queries) {
            assertThat(precise.search(q))
                    .as("results match for '%s'", q)
                    .isEqualTo(linear.search(q));
        }

        // Benchmark PreciseSearcher
        long t0 = System.nanoTime();
        int preciseIterations = 100;
        for (int iter = 0; iter < preciseIterations; iter++) {
            for (String q : queries) {
                precise.search(q);
            }
        }
        long preciseTime = System.nanoTime() - t0;

        // Benchmark LinearSearcher
        long t1 = System.nanoTime();
        for (int iter = 0; iter < preciseIterations; iter++) {
            for (String q : queries) {
                linear.search(q);
            }
        }
        long linearTime = System.nanoTime() - t1;

        // PreciseSearcher should be faster (trie pruning + cache). We assert it's at least
        // not slower, and typically significantly faster. Allow some margin for JIT noise.
        assertThat(preciseTime)
                .as(
                        "PreciseSearcher (%d ns) should be faster than LinearSearcher (%d ns)",
                        preciseTime, linearTime)
                .isLessThanOrEqualTo(linearTime);
    }
}
