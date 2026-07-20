package dev.vfyjxf.phonin.core;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.core.search.Searchers;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;

/**
 * A long-lived, CPU-busy JVM for JProfiler sampling (not a unit test). Loads the dataset, builds a
 * 100k-name TreeSearcher and an AcceleratedQuery, then loops the three hot match paths forever so
 * {@code get_performance_hotspots} can sample where time is spent:
 *
 * <ul>
 *   <li>direct {@link PhonIn#contains} (the {@link dev.vfyjxf.phonin.core.match.Matcher} path)
 *   <li>{@link AcceleratedQuery#contains} (the compile / stable-query path)
 *   <li>{@link Searcher#search} (the {@link dev.vfyjxf.phonin.core.search.TreeSearcher} trie path)
 * </ul>
 */
public class HeatProbe {

    public static void main(String[] args) {
        PhonIn phonIn = PhonIn.create();
        Options o = Options.mandarinQuanpin();
        String[] texts = {"中国", "美国", "中华人民共和国", "中文拼音", "大会人民", "左右前后"};
        String[] queries = {"zhong", "guo", "zhongguo", "a", "z", "ren"};

        Searcher<String> s = Searchers.tree(SearcherLogic.CONTAIN, o);
        String alphabet = "中国大会人民上下左右";
        for (int i = 0; i < 100_000; i++) {
            StringBuilder sb = new StringBuilder(3);
            int n = i;
            for (int j = 0; j < 3; j++) {
                sb.append(alphabet.charAt(n % alphabet.length()));
                n /= alphabet.length();
            }
            s.put(sb.toString(), sb.toString());
        }
        AcceleratedQuery aq = phonIn.compile("zhong", o);

        System.out.println("HEATPROBE_READY");
        System.out.flush();
        // Burn CPU across all three hot paths; no I/O in the loop (would pollute the sample).
        while (true) {
            for (String t : texts) {
                for (String q : queries) phonIn.contains(t, q, o);
            }
            for (String t : texts) aq.contains(t);
            for (String q : queries) s.search(q);
        }
    }
}
