package io.phonin.bench;

import io.phonin.AcceleratedQuery;
import io.phonin.Options;
import io.phonin.PhonIn;
import io.phonin.PhoneticSystem;
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
public class DirectContainsBench {

    @Param({"zhong", "ren", "hua", "da", "z"})
    public String query;

    private PhonIn phonIn;
    private Options options;
    private AcceleratedQuery compiled;
    private String text;

    @Setup
    public void setup() {
        phonIn = PhonIn.create(PhoneticSystem.mandarin);
        options = Options.mandarinQuanpin();
        compiled = phonIn.compile(query, options);
        if ("zhong".equals(query)) {
            text = "中国大会";
        } else if ("ren".equals(query)) {
            text = "中国人民银行";
        } else if ("hua".equals(query)) {
            text = "中华人民共和国";
        } else if ("da".equals(query)) {
            text = "大会";
        } else if ("z".equals(query)) {
            text = "中世纪";
        } else {
            text = "";
        }
    }

    @Benchmark
    public boolean directContains() {
        return phonIn.contains(text, query, options);
    }

    @Benchmark
    public boolean compiledContains() {
        return compiled.contains(text);
    }
}
