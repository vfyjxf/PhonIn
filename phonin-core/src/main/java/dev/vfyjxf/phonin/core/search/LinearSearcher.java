package dev.vfyjxf.phonin.core.search;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.core.match.MatchContext;
import dev.vfyjxf.phonin.core.match.Matcher;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Searcher} that scans every indexed name linearly using the direct {@link Matcher}, with
 * no {@link dev.vfyjxf.phonin.core.match.Accelerator} cache. This is the <em>only</em> built-in
 * searcher that supports {@link dev.vfyjxf.phonin.PolyphoneMode#PRECISE} without index-time
 * interning: the position-aware {@link MatchContext#charNode(int[], int)} path needs a per-search
 * matching frame, which the accelerator's shared per-codepoint cache cannot provide.
 *
 * <p>O(N) per search (one {@link Matcher} call per name), with no cross-name memoization. For OFF
 * mode (the default) prefer {@link SimpleSearcher} or {@link TreeSearcher}, which cache phoneme
 * matches via the accelerator. Use this searcher when PRECISE polyphone disambiguation is active
 * and the index size is moderate (the typical JEI item-list scale — thousands, not millions).
 */
public final class LinearSearcher<T> implements Searcher<T> {

    private final SearcherLogic logic;
    private final Options options;
    private final List<int[]> names = new ArrayList<>();
    private final List<T> objs = new ArrayList<>();

    public LinearSearcher(SearcherLogic logic, Options options) {
        this.logic = logic;
        this.options = options;
    }

    @Override
    public void put(String name, T identifier) {
        names.add(name.codePoints().toArray());
        objs.add(identifier);
    }

    @Override
    public List<T> search(String query) {
        query = options.keyboard().normalizeQuery(query);
        List<T> ret = new ArrayList<>();
        // One context per search: the CharNode cache is keyed by codepoint (valid across names),
        // and PRECISE segmentation re-runs when the text array reference changes.
        MatchContext ctx = new MatchContext(options);
        for (int i = 0; i < objs.size(); i++) {
            int[] name = names.get(i);
            boolean hit;
            switch (logic) {
                case BEGIN:
                    hit = Matcher.begins(name, query, ctx);
                    break;
                case EQUAL:
                    hit = Matcher.matches(name, query, ctx);
                    break;
                default:
                    hit = Matcher.contains(name, query, ctx);
                    break;
            }
            if (hit) ret.add(objs.get(i));
        }
        return ret;
    }

    @Override
    public Options options() {
        return options;
    }

    @Override
    public SearcherLogic logic() {
        return logic;
    }

    public int size() {
        return objs.size();
    }
}
