package dev.vfyjxf.phonin.core.search;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.core.match.Accelerator;
import dev.vfyjxf.phonin.core.util.IndexSet;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * A {@link Searcher} backed by a {@link CodepointTrie} over the indexed names. Ported from {@code
 * me.towdium.pinin.searchers.TreeSearcher}; changed from {@code char} to {@code int} codepoints so
 * supplementary-plane CJK is handled as single elements. ({@code NAcc}-style initial-index
 * descent-pruning was evaluated but measured ineffective — {@code NSlice} already compresses shared
 * prefixes, so few {@code NMap} branching nodes remain to prune — and is not used.)
 *
 * <p>Names live in a shared {@link Compressor} pool and are referenced by pool offset; the trie
 * keys are the codepoints themselves. For {@link SearcherLogic#CONTAIN}, each name is inserted at
 * every codepoint position (so the query may match anywhere); for {@link SearcherLogic#BEGIN}/
 * {@link SearcherLogic#EQUAL}, only at position 0. Search descends the trie guided by {@link
 * Accelerator#get(int, int)} (the cached match-lengths for each codepoint at each query offset).
 * Results are deduplicated and returned in insertion order.
 */
public final class TreeSearcher<T> implements Searcher<T> {

    private final SearcherLogic logic;
    private final Options options;
    final Accelerator acc;
    final Compressor strs = new Compressor();
    private final List<T> objects = new ArrayList<>();
    private final CodepointTrie trie = new CodepointTrie();

    private final CodepointTrie.Guide guide =
            new CodepointTrie.Guide() {
                @Override
                public boolean exact() {
                    return logic == SearcherLogic.EQUAL;
                }

                @Override
                public int queryLength() {
                    return acc.query().length();
                }

                @Override
                public int keyAt(int pos) {
                    return strs.codepoint(pos);
                }

                @Override
                public boolean poolEnd(int pos) {
                    return strs.end(pos);
                }

                @Override
                public IndexSet match(int key, int queryOff) {
                    return acc.get(key, queryOff);
                }

                @Override
                public boolean check(int queryOff, int pos) {
                    return acc.check(queryOff, strs, pos);
                }
            };

    public TreeSearcher(SearcherLogic logic, Options options) {
        this.logic = logic;
        this.options = options;
        this.acc = new Accelerator(options, logic.partial);
    }

    @Override
    public void put(String name, T identifier) {
        int pos = strs.put(name);
        int id = objects.size();
        // CONTAIN anchors the query at every codepoint position; an empty name still gets position
        // 0 so it is findable by an empty query (matching the direct matcher).
        int anchors = logic == SearcherLogic.CONTAIN ? Math.max(1, strs.size() - pos - 1) : 1;
        trie.insert(guide, pos, anchors, id);
        objects.add(identifier);
    }

    @Override
    public List<T> search(String query) {
        acc.search(options.keyboard().normalizeQuery(query));
        // Object ids are dense (0..N-1), so a BitSet dedups in O(1) (no hashing/rehash) and
        // nextSetBit yields them ascending (no separate sort) — beating IntOpenHashSet, whose
        // rehash and empty-slot-skipping iterator dominated the search cost.
        BitSet ret = new BitSet(objects.size());
        trie.collect(guide, ret);
        List<T> out = new ArrayList<>(ret.cardinality());
        for (int i = ret.nextSetBit(0); i >= 0; i = ret.nextSetBit(i + 1)) {
            out.add(objects.get(i));
        }
        return out;
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
        return objects.size();
    }
}
