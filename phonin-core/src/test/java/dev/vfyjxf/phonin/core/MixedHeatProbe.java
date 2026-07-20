package dev.vfyjxf.phonin.core;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.core.search.Searchers;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;

/**
 * A long-lived, CPU-busy JVM for JProfiler sampling of a 中英混合 (CJK + latin) index — complements
 * {@link HeatProbe} (pure CJK) with a workload whose trie descent is deep (latin text matches one
 * query char per level, vs CJK where one char consumes a whole syllable). Three cohorts in one
 * index: CJK names (shallow descent), latin names (deep descent), and genuinely mixed names (deep
 * latin prefix then CJK). Used during search-perf work to confirm where time goes across workload
 * shapes.
 */
public class MixedHeatProbe {

    public static void main(String[] args) {
        PhonIn.create(); // lazy; Mandarin data will be loaded on first search
        Options o = Options.mandarinQuanpin();
        Searcher<String> s = Searchers.tree(SearcherLogic.CONTAIN, o);

        String cjk = "中国大会人民上下左右"; // 10 chars
        String latin = "abcdefgh"; // 8 letters -> bushy, deep latin descent
        String cjkTail = "中英文混";

        for (int i = 0; i < 40_000; i++) { // CJK names (3-char) — shallow-descent control
            StringBuilder sb = new StringBuilder(3);
            int n = i;
            for (int j = 0; j < 3; j++) {
                sb.append(cjk.charAt(n % cjk.length()));
                n /= cjk.length();
            }
            s.put(sb.toString(), sb.toString());
        }
        for (int i = 0; i < 40_000; i++) { // latin names (5-char) — deep-descent cohort
            StringBuilder sb = new StringBuilder(5);
            int n = i;
            for (int j = 0; j < 5; j++) {
                sb.append(latin.charAt(n % latin.length()));
                n /= latin.length();
            }
            s.put(sb.toString(), sb.toString());
        }
        for (int i = 0; i < 20_000; i++) { // mixed names: latin prefix + CJK tail
            StringBuilder sb = new StringBuilder(6);
            int n = i;
            for (int j = 0; j < 3; j++) {
                sb.append(latin.charAt(n % latin.length()));
                n /= latin.length();
            }
            for (int j = 0; j < 2; j++) {
                sb.append(cjkTail.charAt(n % cjkTail.length()));
                n /= cjkTail.length();
            }
            s.put(sb.toString(), sb.toString());
        }

        // Long specific latin queries -> deep descent, low cardinality (descent dominates).
        // CJK queries -> shallow (collection dominates). A few mixed.
        String[] queries = {
            "abcde", "ababa", "aaaaa", "cdcda", "bcdea", "habcde", "dehabc", "zhong", "guo", "z",
            "ren", "abc中", "hab中"
        };

        System.out.println("MIXEDHEATPROBE_READY");
        System.out.flush();
        while (true) {
            for (String q : queries) s.search(q);
        }
    }
}
