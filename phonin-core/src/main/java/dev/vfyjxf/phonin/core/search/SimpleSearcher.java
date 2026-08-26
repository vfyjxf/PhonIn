package dev.vfyjxf.phonin.core.search;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.core.match.Accelerator;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A {@link Searcher} that scans every indexed name linearly, sharing one {@link Accelerator} for
 * the query so phoneme matches are memoized across names. Adapted from {@code
 * me.towdium.pinin.searchers.SimpleSearcher}; PhonIn stores each name's codepoints directly (no
 * shared pool — that is {@link TreeSearcher}'s concern) and matches via the accelerator's {@code
 * int[]} entry points.
 *
 * <p>O(N) per search regardless of selectivity; prefer {@link TreeSearcher} for large indices.
 */
public final class SimpleSearcher<T> implements Searcher<T> {

    private final SearcherLogic logic;
    private final Options options;
    private final Accelerator acc;
    private final List<int[]> names = new ArrayList<>();
    private final List<T> objs = new ArrayList<>();

    private final Predicate<int[]> tester; // dispatches on logic, avoids a per-name switch

    public SimpleSearcher(SearcherLogic logic, Options options) {
        this.logic = logic;
        this.options = options;
        this.acc = new Accelerator(options, logic.partial);
        this.tester =
                logic == SearcherLogic.BEGIN
                        ? acc::begins
                        : logic == SearcherLogic.EQUAL ? acc::matches : acc::contains;
    }

    @Override
    public void put(String name, T identifier) {
        names.add(name.codePoints().toArray());
        objs.add(identifier);
    }

    @Override
    public List<T> search(String query) {
        acc.search(options.keyboard().normalizeQuery(query));
        List<T> ret = new ArrayList<>();
        for (int i = 0; i < objs.size(); i++) {
            if (tester.test(names.get(i))) ret.add(objs.get(i));
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
