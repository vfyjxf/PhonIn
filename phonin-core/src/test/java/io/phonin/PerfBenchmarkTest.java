package io.phonin;

import io.phonin.fuzzy.FuzzyRules;
import io.phonin.model.CharEntry;
import io.phonin.model.Reading;
import io.phonin.search.Searcher;
import io.phonin.search.SearcherLogic;
import io.phonin.search.Searchers;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A performance + memory survey of the engine, gated behind {@code -Dphonin.bench=true} so it does
 * NOT run as part of {@code ./gradlew check}. Run with:
 *
 * <p>{@code ./gradlew :phonin-core:test --tests io.phonin.PerfBenchmarkTest -Dphonin.bench=true}
 *
 * <p>Parts:
 *
 * <ul>
 *   <li><b>A</b> engine footprint — heap after loading the dataset, per-system char counts.
 *   <li><b>B</b> direct matcher throughput (µs/call) — across systems, options, modes, text length.
 *   <li><b>C</b> {@link AcceleratedQuery} — compile time + memory; compiled vs direct speedup.
 *   <li><b>D</b> {@link Searcher} build + search — time AND index footprint, Simple vs Tree × size.
 * </ul>
 *
 * <p>Times are best-of-3 (µs); memory deltas are Runtime-based after GC (approximate, in KB/MB).
 * There are no assertions — perf is environment-dependent.
 */
class PerfBenchmarkTest {

    private static final boolean ENABLED = Boolean.getBoolean("phonin.bench");
    private static final String ALPHABET = "中国大会人民上下左右";

    /** Holds the most recently measured object so it survives the post-build GC. */
    private Object hold;

    @Test
    void survey() {
        Assumptions.assumeTrue(ENABLED, "set -Dphonin.bench=true to run the perf survey");
        System.out.println();
        System.out.println("================ PhonIn perf + memory survey ================");

        // Part A loads the dataset (first touch) and measures its footprint, then returns it.
        PhonIn phonIn = partAEngineFootprint();
        partBDirectMatcher(phonIn);
        partCAcceleratedQuery(phonIn);
        partDSearchers(phonIn);
        partEHugeScale(phonIn);
    }

    // ---- Part A: engine footprint ---------------------------------------------------------

    private PhonIn partAEngineFootprint() {
        System.out.println();
        System.out.println(
                "--- Part A: per-language memory footprint (char data, loaded in isolation) ---");
        // Bundled load (all 5 systems together, as PhonIn.createAll() does in practice):
        long empty = heapAfterGc(null);
        PhonIn phonIn = PhonIn.createAll(); // eagerly loads all bundled systems
        long withEngine = heapAfterGc(() -> hold = phonIn);
        System.out.printf("  bundled (all 5 systems): %6.1f MB%n", mb(withEngine - empty));

        // Per-language: load each system's char table into a FRESH PhoneticSystem and measure it
        // alone, so the cost of each language is visible independently.
        String[][] systems = {
            {"mandarin", "DIGIT", "true"},
            {"cantonese", "DIGIT", "false"},
            {"zhuyin", "ZHUYIN", "false"},
            {"japanese", "NONE", "false"},
            {"korean", "NONE", "false"},
        };
        System.out.printf(
                "  %-10s %-8s %-12s %-14s%n", "system", "chars", "heap(KB)", "bytes/char");
        // Accumulate loaded systems (keep all alive) so each delta is its incremental retained
        // cost,
        // not polluted by the previous system being released. Warm up the TSV-parse path first.
        loadChars("korean", ToneConvention.NONE, false);
        List<PhoneticSystem> accumulated = new ArrayList<>();
        long total = 0;
        long totalChars = 0;
        for (String[] sys : systems) {
            String name = sys[0];
            ToneConvention tc = ToneConvention.valueOf(sys[1]);
            boolean abbr = Boolean.parseBoolean(sys[2]);
            System.gc();
            System.gc();
            long before = usedHeap();
            PhoneticSystem s = loadChars(name, tc, abbr);
            accumulated.add(s); // retain so it is not reclaimed before the after-measurement
            System.gc();
            System.gc();
            long mem = usedHeap() - before;
            int chars = s.charCount();
            total += mem;
            totalChars += chars;
            System.out.printf(
                    "  %-10s %-8d %-12.1f %-14.0f%n",
                    name, chars, kb(mem), chars == 0 ? 0 : mem / (double) chars);
        }
        System.out.printf("  %-10s %-8d %-12.1f%n", "SUM", totalChars, kb(total));
        return phonIn;
    }

    // ---- Part B: direct matcher throughput ------------------------------------------------

    private void partBDirectMatcher(PhonIn phonIn) {
        System.out.println();
        System.out.println("--- Part B: direct matcher throughput (µs/call, best-of-3) ---");
        // Per system: a guaranteed-matching (char, 2-char-prefix query).
        System.out.println("  contains, single-char, by system:");
        for (PhoneticSystem s :
                new PhoneticSystem[] {
                    PhoneticSystem.mandarin,
                    PhoneticSystem.cantonese,
                    PhoneticSystem.zhuyin,
                    PhoneticSystem.japanese,
                    PhoneticSystem.korean
                }) {
            String[] mq = firstMatch(s);
            if (mq == null) continue;
            Options o = Options.builder(s).build();
            double us = perCall(20000, () -> phonIn.contains(mq[0], mq[1], o));
            System.out.printf("    %-10s (%s / \"%s\")  %6.3f µs%n", s, mq[0], mq[1], us);
        }

        // Mandarin across options / modes / text length.
        System.out.println("  mandarin, by option / mode / text length:");
        Options quanpin = Options.mandarinQuanpin();
        Options shuangpin = SystemOptions.mandarinShuangpin("flypy");
        Options fuzzy =
                Options.builder(PhoneticSystem.mandarin).addFuzzy(FuzzyRules.fuzzyZhZ).build();
        Options abbrev =
                Options.builder(PhoneticSystem.mandarin).abbrev(AbbrevPolicy.INITIALS).build();
        Options strict = Options.builder(PhoneticSystem.mandarin).tone(TonePolicy.STRICT).build();
        row(phonIn, "quanpin contains 1-char", "中", "zhong", quanpin);
        row(phonIn, "quanpin contains 3-char", "中国大", "zhong", quanpin);
        row(phonIn, "quanpin begins    3-char", "中国大", "zhong", quanpin, "BEGINS");
        row(phonIn, "quanpin matches   3-char", "中国大", "zhongguoda", quanpin, "EXACT");
        row(phonIn, "shuangpin contains 1-char", "中", "vs", shuangpin);
        row(phonIn, "fuzzy    contains 1-char", "中", "zong", fuzzy);
        row(phonIn, "abbrev   contains 3-char", "中国大", "zgd", abbrev);
        row(phonIn, "strict   matches  1-char", "中", "zhong1", strict, "EXACT");
    }

    private void row(PhonIn p, String label, String text, String query, Options o) {
        row(p, label, text, query, o, "CONTAINS");
    }

    private void row(PhonIn p, String label, String text, String query, Options o, String mode) {
        double us =
                perCall(
                        20000,
                        () -> {
                            switch (mode) {
                                case "BEGINS":
                                    p.begins(text, query, o);
                                    break;
                                case "EXACT":
                                    p.matches(text, query, o);
                                    break;
                                default:
                                    p.contains(text, query, o);
                            }
                        });
        System.out.printf("    %-28s %6.3f µs%n", label, us);
    }

    // ---- Part C: AcceleratedQuery ---------------------------------------------------------

    private void partCAcceleratedQuery(PhonIn phonIn) {
        System.out.println();
        System.out.println("--- Part C: AcceleratedQuery (compile = stable-query path) ---");
        Options o = Options.mandarinQuanpin();
        List<String> texts = names(2000, 3);
        double directUs =
                total(
                        3,
                        () -> {
                            for (String t : texts) phonIn.contains(t, "zhong", o);
                        });
        long compileMem = footprint(() -> hold = phonIn.compile("zhong", o));
        AcceleratedQuery aq = phonIn.compile("zhong", o);
        double compiledUs =
                total(
                        3,
                        () -> {
                            for (String t : texts) aq.contains(t);
                        });
        System.out.printf("  compile() footprint:           %6.1f KB%n", kb(compileMem));
        System.out.printf("  2000 texts direct contains:    %6.1f µs%n", directUs);
        System.out.printf("  2000 texts compiled contains:  %6.1f µs%n", compiledUs);
        System.out.printf("  speedup:                       %6.2fx%n", directUs / compiledUs);
    }

    // ---- Part D: Searcher build + search --------------------------------------------------

    private void partDSearchers(PhonIn phonIn) {
        System.out.println();
        System.out.println("--- Part D: Searcher build + search (time + index footprint) ---");
        Options o = Options.mandarinQuanpin();
        String[] queries = {
            "zhong", "guo", "da", "ren", "shang", "xia", "a", "z", "qqqx", "zhongguo"
        };
        System.out.printf(
                "  %-7s %-7s %-12s %-12s %-12s%n",
                "size", "impl", "build(ms)", "index(KB)", "search/10q(µs)");
        for (int size : new int[] {200, 2000, 20000}) {
            List<String> names = names(size, 3);
            for (boolean tree : new boolean[] {false, true}) {
                // build(ms): time a throwaway build (GC perturbs footprint timing, so time first).
                long t0 = System.nanoTime();
                buildSearcher(tree, SearcherLogic.CONTAIN, o, names);
                long buildNs = System.nanoTime() - t0;
                // index(KB): build a FRESH searcher inside the footprint so its allocation is
                // counted.
                long indexBytes =
                        footprint(
                                () -> hold = buildSearcher(tree, SearcherLogic.CONTAIN, o, names));
                @SuppressWarnings("unchecked")
                Searcher<String> s = (Searcher<String>) hold;
                s.search("zhong"); // warm
                double searchUs =
                        total(
                                3,
                                () -> {
                                    for (String q : queries) s.search(q);
                                });
                System.out.printf(
                        "  %-7d %-7s %-12.2f %-12.1f %-12.1f%n",
                        size,
                        tree ? "tree" : "simple",
                        buildNs / 1_000_000.0,
                        kb(indexBytes),
                        searchUs);
            }
        }
        // Logic breakdown for tree at size 2000.
        System.out.println("  tree @2000, by logic:");
        List<String> names2k = names(2000, 3);
        for (SearcherLogic logic : SearcherLogic.values()) {
            Searcher<String> s = Searchers.tree(logic, o);
            for (String n : names2k) s.put(n, n);
            s.search("zhong");
            double us =
                    total(
                            3,
                            () -> {
                                for (String q : queries) s.search(q);
                            });
            System.out.printf("    %-8s %6.1f µs/10q%n", logic, us);
        }
    }

    // ---- helpers --------------------------------------------------------------------------

    /**
     * First codepoint in the CJK+Hangul range with a reading in {@code s}; returns {char,
     * 2-prefix}.
     */
    /**
     * Build a fresh searcher of the given kind, index {@code names} (as their own ids), return it.
     */
    // ---- Part E: huge-scale search --------------------------------------------------------

    private void partEHugeScale(PhonIn phonIn) {
        System.out.println();
        System.out.println("--- Part E: huge-scale search (beyond JEI; broad-node stress) ---");
        Options o = Options.mandarinQuanpin();
        int[] alpha = mandarinAlphabet(256);
        System.out.printf(
                "  alphabet: %d real mandarin chars; 3-char names (level-2 nodes up to %d children)%n",
                alpha.length, alpha.length);
        String[] queries = {"zhong", "a", "z", "qqqq", "ren"};
        System.out.printf(
                "  %-9s %-7s %-12s %-12s %-14s%n",
                "size", "impl", "build(ms)", "index(MB)", "search/5q(ms)");
        for (int size : new int[] {100_000, 500_000, 1_000_000}) {
            List<String> names = hugeNames(size, alpha);
            // Simple only at 100k — O(N) search is prohibitive past that.
            if (size == 100_000) {
                runHuge(false, size, names, o, queries);
            }
            runHuge(true, size, names, o, queries);
        }
    }

    /** Build (timed) + measure index footprint + search, single build, one printed row. */
    private void runHuge(boolean tree, int size, List<String> names, Options o, String[] queries) {
        System.gc();
        System.gc();
        long before = usedHeap();
        long t0 = System.nanoTime();
        Searcher<String> s = buildSearcher(tree, SearcherLogic.CONTAIN, o, names);
        long buildNs = System.nanoTime() - t0;
        hold = s; // retain across the post-build GC
        System.gc();
        System.gc();
        long mem = usedHeap() - before;
        s.search("zhong"); // warm
        double searchMs =
                total(
                                3,
                                () -> {
                                    for (String q : queries) s.search(q);
                                })
                        / 1000.0;
        System.out.printf(
                "  %-9d %-7s %-12.0f %-12.1f %-14.2f%n",
                size, tree ? "tree" : "simple", buildNs / 1_000_000.0, mb(mem), searchMs);
    }

    /** The first {@code k} codepoints from the mandarin char table (a real-char alphabet). */
    private static int[] mandarinAlphabet(int k) {
        List<Integer> cps = new ArrayList<>();
        readTsv(
                "phonin/raw/mandarin-char.tsv",
                line -> {
                    if (cps.size() >= k) return;
                    String[] c = line.split("\t", -1);
                    if (c.length >= 5) cps.add(parseCp(c[0]));
                });
        int[] out = new int[cps.size()];
        for (int i = 0; i < cps.size(); i++) out[i] = cps.get(i);
        return out;
    }

    /** {@code count} distinct names over {@code alpha}, long enough to be unique, MSB-first. */
    private static List<String> hugeNames(int count, int[] alpha) {
        int base = alpha.length;
        int len = 1;
        long cap = base;
        while (cap < count) {
            cap *= base;
            len++;
        }
        List<String> out = new ArrayList<>(count);
        int[] pow = new int[len];
        pow[len - 1] = 1;
        for (int j = len - 2; j >= 0; j--) pow[j] = pow[j + 1] * base;
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder(len);
            int n = i;
            for (int j = 0; j < len; j++) {
                sb.appendCodePoint(alpha[n / pow[j]]);
                n %= pow[j];
            }
            out.add(sb.toString());
        }
        return out;
    }

    private static Searcher<String> buildSearcher(
            boolean tree, SearcherLogic logic, Options o, List<String> names) {
        Searcher<String> s = tree ? Searchers.tree(logic, o) : Searchers.simple(logic, o);
        for (String n : names) s.put(n, n);
        return s;
    }

    /** Load one system's char table into a FRESH PhoneticSystem (isolates its memory cost). */
    private static PhoneticSystem loadChars(String name, ToneConvention tc, boolean abbreviable) {
        PhoneticSystem s = new PhoneticSystem(name.toUpperCase(), abbreviable, null, tc);
        readTsv(
                "phonin/raw/" + name + "-char.tsv",
                line -> {
                    String[] c = line.split("\t", -1);
                    if (c.length < 5) return;
                    int cp = parseCp(c[0]);
                    String[] norm = c[3].split(",");
                    List<Reading> readings = new ArrayList<>();
                    for (int i = 0; i < norm.length; i++) {
                        String n = norm[i].trim();
                        if (n.isEmpty()) continue;
                        readings.add(new Reading(s, n));
                    }
                    if (!readings.isEmpty()) s.putChar(new CharEntry(cp, readings));
                });
        return s;
    }

    private static void readTsv(String resource, Consumer<String> lineFn) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = PerfBenchmarkTest.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource);
                BufferedReader r =
                        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                lineFn.accept(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + resource, e);
        }
    }

    private static int parseCp(String s) {
        String h = s.startsWith("U+") ? s.substring(2) : s;
        return (int) Long.parseLong(h, 16);
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String[] firstMatch(PhoneticSystem s) {
        for (int cp = 0x3400; cp <= 0xD7AF; cp++) {
            CharEntry e = s.charEntry(cp);
            if (e != null && !e.readings().isEmpty()) {
                Reading r = e.readings().get(0);
                String surface = s.toneConvention().strip(r.normalized);
                String q = surface.length() >= 2 ? surface.substring(0, 2) : surface;
                if (q.isEmpty()) continue;
                return new String[] {new String(Character.toChars(cp)), q};
            }
        }
        return null;
    }

    private static List<String> names(int count, int len) {
        int base = ALPHABET.length();
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int n = i;
            char[] d = new char[len];
            for (int j = 0; j < len; j++) {
                d[j] = ALPHABET.charAt(n % base);
                n /= base;
            }
            out.add(new String(d));
        }
        return out;
    }

    /** Best-of-{@code rounds} total micros for {@code body}. */
    private static double total(int rounds, Runnable body) {
        body.run();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < rounds; i++) {
            long t = System.nanoTime();
            body.run();
            best = Math.min(best, System.nanoTime() - t);
        }
        return best / 1000.0;
    }

    /** Best-of-3 micros/call over {@code iters} iterations of {@code body}. */
    private static double perCall(int iters, Runnable body) {
        body.run();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            long t = System.nanoTime();
            for (int j = 0; j < iters; j++) body.run();
            best = Math.min(best, System.nanoTime() - t);
        }
        return (best / 1000.0) / iters;
    }

    /**
     * Heap delta (bytes) of running {@code build} (which must retain its result in {@link #hold}).
     */
    private static long footprint(Runnable build) {
        Runtime rt = Runtime.getRuntime();
        System.gc();
        System.gc();
        long before = rt.totalMemory() - rt.freeMemory();
        build.run();
        System.gc();
        System.gc();
        long after = rt.totalMemory() - rt.freeMemory();
        return after - before;
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static long heapAfterGc(Runnable before) {
        if (before != null) before.run();
        Runtime rt = Runtime.getRuntime();
        System.gc();
        System.gc();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static double kb(long bytes) {
        return bytes / 1024.0;
    }

    private static double mb(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }
}
