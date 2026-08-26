package dev.vfyjxf.phonin.core.match;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PolyphoneMode;
import dev.vfyjxf.phonin.core.elements.CharNode;
import dev.vfyjxf.phonin.core.util.IndexSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;

/**
 * A per-query match accelerator: memoizes each codepoint's {@link IndexSet} (the query-lengths its
 * {@link CharNode} can consume) per query offset, so that matching many names or texts against ONE
 * query reuses the phoneme-match computation. Adapted from {@code
 * me.towdium.pinin.utils.Accelerator} for PhonIn's model.
 *
 * <p>Every lookup goes through {@link MatchContext#charNode(int)} + {@link CharNode#match}, so
 * fuzzy / keyboard / tone / shuangpin policy is <em>identical</em> to the direct {@link Matcher} —
 * the {@link dev.vfyjxf.phonin.core.AcceleratedQuery} and {@link
 * dev.vfyjxf.phonin.core.search.Searcher} paths cannot diverge from one-off matching because they
 * share this code path. Backtracking itself lives in {@link Backtrack}.
 *
 * <p>Cached {@link IndexSet}s are read-only (callers use only {@code get} / the raw bitmask), so
 * one instance is shared safely. Not thread-safe.
 */
public final class Accelerator {

    /**
     * A codepoint sequence the accelerator walks by position (a shared name pool).
     */
    public interface Provider {
        /**
         * Position {@code i} is at/after the end (sentinel or beyond the sequence).
         */
        boolean end(int i);

        /**
         * The codepoint at {@code i}; only valid when {@link #end(int)} is false.
         */
        int codepoint(int i);
    }

    private final Options options;
    private final MatchContext context;
    private boolean partial;
    private String query = "";

    // One map per query offset: codepoint -> the cached IndexSet for CharNode.match at that offset.
    // A fastutil primitive map so the hot-path get/put do not box the codepoint to Integer -- the
    // trie calls acc.get once per child node visited, so this stays a tight, allocation-free
    // lookup.
    private final List<Int2ObjectOpenHashMap<IndexSet>> cache = new ArrayList<>();

    private final PoolSource poolSource = new PoolSource();
    private final ArraySource arraySource = new ArraySource();

    public Accelerator(Options options, boolean partial) {
        if (options.polyphoneMode() == PolyphoneMode.PRECISE) {
            throw new IllegalArgumentException(
                    "precise polyphone mode is unsupported on the accelerator/searcher path");
        }
        this.options = options;
        this.context = new MatchContext(options);
        this.partial = partial;
    }

    public Options options() {
        return options;
    }

    public boolean partial() {
        return partial;
    }

    /**
     * Set the partial flag (BEGIN/CONTAIN use {@code true}, EQUAL uses {@code false}). The cache is
     * cleared when it changes, since the cached IndexSets depend on it.
     */
    public void partial(boolean partial) {
        if (this.partial != partial) {
            this.partial = partial;
            cache.clear();
        }
    }

    public String query() {
        return query;
    }

    /**
     * Bind this accelerator to a query; resets the cache if the query changed.
     */
    public void search(String query) {
        if (!query.equals(this.query)) {
            this.query = query;
            cache.clear();
        }
    }

    /**
     * The cached set of query-lengths {@code codepoint} can consume starting at {@code offset}.
     */
    public IndexSet get(int codepoint, int offset) {
        while (cache.size() <= offset) cache.add(null);
        Int2ObjectOpenHashMap<IndexSet> layer = cache.get(offset);
        if (layer == null) {
            layer = new Int2ObjectOpenHashMap<>();
            cache.set(offset, layer);
        }
        IndexSet ret = layer.get(codepoint);
        if (ret == null) {
            ret = context.charNode(codepoint).match(query, offset, partial);
            layer.put(codepoint, ret);
        }
        return ret;
    }

    //region whole-name tests against a Provider

    /**
     * CONTAIN: the query matches starting at any position in {@code p} from {@code start}.
     */
    public boolean contains(Provider p, int start) {
        if (query.isEmpty()) return true;
        for (int i = start; !p.end(i); i++) {
            if (check(0, p, i)) return true;
        }
        return false;
    }

    /**
     * BEGIN: the query matches starting exactly at {@code start}, with a partial tail.
     */
    public boolean begins(Provider p, int start) {
        if (p.end(start)) return query.isEmpty();
        return check(0, p, start);
    }

    /**
     * EQUAL: the query consumes {@code p} exactly from {@code start} (no partial tail).
     */
    public boolean matches(Provider p, int start) {
        if (p.end(start)) return query.isEmpty();
        return check(0, p, start);
    }

    /**
     * Resume matching from a partial query offset; exposed for the {@link
     * dev.vfyjxf.phonin.core.search.TreeSearcher} trie.
     */
    public boolean check(int queryOff, Provider p, int start) {
        poolSource.p = p;
        return Backtrack.run(poolSource, start, query, queryOff, partial);
    }

    //endregion
    //region int[] entry points (AcceleratedQuery / SimpleSearcher)

    public boolean contains(int[] name) {
        if (query.isEmpty()) return true;
        arraySource.a = name;
        for (int i = 0; i < name.length; i++) {
            if (Backtrack.run(arraySource, i, query, 0, partial)) return true;
        }
        return false;
    }

    public boolean begins(int[] name) {
        if (name.length == 0) return query.isEmpty();
        arraySource.a = name;
        return Backtrack.run(arraySource, 0, query, 0, partial);
    }

    public boolean matches(int[] name) {
        if (name.length == 0) return query.isEmpty();
        arraySource.a = name;
        return Backtrack.run(arraySource, 0, query, 0, partial);
    }

    //endregion

    private final class PoolSource implements Backtrack.Source {
        Provider p;

        @Override
        public boolean end(int pos) {
            return p.end(pos);
        }

        @Override
        public IndexSet matchAt(int pos, int queryOff) {
            return get(p.codepoint(pos), queryOff);
        }
    }

    private final class ArraySource implements Backtrack.Source {
        int[] a;

        @Override
        public boolean end(int pos) {
            return pos >= a.length;
        }

        @Override
        public IndexSet matchAt(int pos, int queryOff) {
            return get(a[pos], queryOff);
        }
    }
}
