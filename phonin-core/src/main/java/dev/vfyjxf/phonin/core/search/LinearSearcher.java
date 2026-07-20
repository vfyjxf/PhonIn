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
 * no {@link dev.vfyjxf.phonin.core.match.Accelerator} cache. Each name gets a fresh per-call {@link
 * MatchContext}, so this is the <em>only</em> built-in searcher that supports {@link
 * dev.vfyjxf.phonin.PolyphoneMode#PRECISE}: the position-aware {@link MatchContext#charNode(int[],
 * int)} path requires a per-name matching frame, which the accelerator's shared per-codepoint cache
 * cannot provide.
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
        List<T> ret = new ArrayList<>();
        for (int i = 0; i < objs.size(); i++) {
            int[] name = names.get(i);
            MatchContext ctx = new MatchContext(options);
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
