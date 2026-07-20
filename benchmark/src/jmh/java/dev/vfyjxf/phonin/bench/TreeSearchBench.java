package dev.vfyjxf.phonin.bench;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.core.PhonIn;
import dev.vfyjxf.phonin.core.search.Searchers;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class TreeSearchBench {

    @Param({"zhong", "da", "hua", "ren", "z"})
    public String query;

    private Searcher<String> searcher;

    @Setup
    public void setup() throws Exception {
        PhonIn phonIn = PhonIn.create(PhoneticSystem.mandarin);
        Options options = Options.mandarinQuanpin();
        searcher = Searchers.tree(SearcherLogic.CONTAIN, options);
        String dataPath = System.getProperty("phonin.bench.treeData");
        List<String> lines;
        if (dataPath != null && !dataPath.isEmpty()) {
            lines = Files.readAllLines(Paths.get(dataPath));
        } else {
            // Small built-in fallback so the benchmark still compiles and can run without
            // an external dataset. Use -Dphonin.bench.treeData=<path> for realistic data.
            lines =
                    Arrays.asList(
                            "中国", "美国", "日本", "大会", "人民", "银行", "中华", "共和国", "中世纪", "中央", "世界",
                            "全国", "大会", "公司", "发展", "经济", "社会", "文化", "技术", "工程");
        }
        for (String n : lines) {
            searcher.put(n, n);
        }
    }

    @Benchmark
    public List<String> search() {
        return searcher.search(query);
    }
}
