package io.phonin.bench;

import io.phonin.Options;
import io.phonin.PhonIn;
import io.phonin.PhoneticSystem;
import io.phonin.search.Searcher;
import io.phonin.search.SearcherLogic;
import io.phonin.search.Searchers;
import java.nio.file.Files;
import java.nio.file.Paths;
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
        List<String> lines = Files.readAllLines(Paths.get("/tmp/PinIn4Cpp/test_data/small.txt"));
        for (String n : lines) {
            searcher.put(n, n);
        }
    }

    @Benchmark
    public List<String> search() {
        return searcher.search(query);
    }
}
