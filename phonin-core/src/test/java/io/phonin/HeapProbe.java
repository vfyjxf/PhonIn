package io.phonin;

import io.phonin.search.Searcher;
import io.phonin.search.SearcherLogic;
import io.phonin.search.Searchers;
import java.util.ArrayList;
import java.util.List;

/**
 * A long-lived JVM for JProfiler heap analysis (not a unit test; run via {@code java -cp ...}).
 * Loads the dataset (and optionally a TreeSearcher index) then blocks, so {@code create_heap_dump}
 * can snapshot its precise retained memory. Args:
 *
 * <ul>
 *   <li>{@code dataset} (default) — just {@link PhonIn#create()}.
 *   <li>{@code index <n>} — dataset + a TreeSearcher over {@code n} synthetic 3-char names.
 * </ul>
 */
public class HeapProbe {

    public static void main(String[] args) throws Exception {
        PhonIn phonIn = PhonIn.create(PhoneticSystem.mandarin);
        String mode = args.length == 0 ? "dataset" : args[0];
        Searcher<String> holdIndex = null;
        if (mode.equals("index") && args.length >= 2) {
            int n = Integer.parseInt(args[1]);
            holdIndex = buildIndex(n);
        }
        // Keep references alive so GC does not reclaim them before the dump.
        System.out.println(
                "HEAPPROBE_READY mode="
                        + mode
                        + " mandarinChars="
                        + PhoneticSystem.mandarin.charCount()
                        + (holdIndex == null
                                ? ""
                                : " indexSize=" + (args.length >= 2 ? args[1] : "?")));
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
    }

    private static Searcher<String> buildIndex(int n) {
        String alphabet = "中国大会人民上下左右大事好人前后出"; // ~14 chars -> shared prefixes
        int base = alphabet.length();
        int len = 1;
        long cap = base;
        while (cap < n) {
            cap *= base;
            len++;
        }
        Searcher<String> s = Searchers.tree(SearcherLogic.CONTAIN, Options.mandarinQuanpin());
        int[] pow = new int[len];
        pow[len - 1] = 1;
        for (int j = len - 2; j >= 0; j--) pow[j] = pow[j + 1] * base;
        List<String> batch = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            StringBuilder sb = new StringBuilder(len);
            int k = i;
            for (int j = 0; j < len; j++) {
                sb.append(alphabet.charAt(k / pow[j]));
                k %= pow[j];
            }
            batch.add(sb.toString());
        }
        for (String name : batch) s.put(name, name);
        return s;
    }
}
