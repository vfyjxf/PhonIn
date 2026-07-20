package io.phonin.search;

import io.phonin.Options;
import io.phonin.match.Accelerator;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A {@link Searcher} backed by a self-tuning codepoint trie over the indexed names. Ported from
 * {@code me.towdium.pinin.searchers.TreeSearcher} (the {@code NDense}→{@code NSlice}→{@code NMap}
 * progression). Changed from {@code char} to {@code int} codepoints so supplementary-plane CJK is
 * handled as single elements. ({@code NAcc}-style initial-index descent-pruning was evaluated but
 * measured ineffective — {@code NSlice} already compresses shared prefixes, so few {@code NMap}
 * branching nodes remain to prune — and is not used.)
 *
 * <p>Names live in a shared {@link Compressor} pool and are referenced by pool offset. For {@link
 * SearcherLogic#CONTAIN}, each name is inserted at every codepoint position (so the query may match
 * anywhere); for {@link SearcherLogic#BEGIN}/{@link SearcherLogic#EQUAL}, only at position 0.
 * Search descends the trie guided by {@link Accelerator#get(int, int)} (the cached match-lengths
 * for each codepoint at each query offset); flat {@link NDense} leaves test the remaining name
 * suffix via {@link Accelerator#check(int, io.phonin.match.Accelerator.Provider, int)}.
 *
 * <p>{@code NDense} (a flat list of up to 128 offset/id pairs) splits into an {@code NSlice}
 * (common-prefix span) once it grows too large; {@code NSlice} dispatches to an {@code NMap}
 * (codepoint→child) at the divergence point. Results are deduplicated (a CONTAIN name can be
 * reached via several anchors) and returned in insertion order.
 */
public final class TreeSearcher<T> implements Searcher<T> {

    /** A node splits from flat to trie-form once it holds this many entries. */
    private static final int THRESHOLD = 128;

    private final SearcherLogic logic;
    private final Options options;
    final Accelerator acc;
    final Compressor strs = new Compressor();
    private final List<T> objects = new ArrayList<>();

    private Node<T> root = new NDense<>();

    public TreeSearcher(SearcherLogic logic, Options options) {
        this.logic = logic;
        this.options = options;
        this.acc = new Accelerator(options, logic.partial);
    }

    @Override
    public void put(String name, T identifier) {
        int pos = strs.put(name);
        int id = objects.size();
        int codepoints = name.codePointCount(0, name.length());
        // CONTAIN anchors the query at every codepoint position; an empty name still gets position
        // 0
        // so it is findable by an empty query (matching the direct matcher).
        int end = logic == SearcherLogic.CONTAIN ? Math.max(1, codepoints) : 1;
        for (int i = 0; i < end; i++) {
            root = root.put(this, pos + i, id);
        }
        objects.add(identifier);
    }

    @Override
    public List<T> search(String query) {
        acc.search(query);
        // Object ids are dense (0..N-1), so a BitSet dedups in O(1) (no hashing/rehash) and
        // nextSetBit yields them ascending (no separate sort) — beating IntOpenHashSet, whose
        // rehash
        // and empty-slot-skipping iterator dominated the search cost.
        BitSet ret = new BitSet(objects.size());
        root.get(this, ret, 0);
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

    /**
     * Longest common prefix length (in pool positions) between two names, capped at {@code max}.
     */
    private int common(int s1, int s2, int max) {
        for (int i = 0; i < max; i++) {
            int a = strs.codepoint(s1 + i);
            int b = strs.codepoint(s2 + i);
            if (a != b || a == 0) return i;
        }
        return max;
    }

    // ---- trie nodes ----

    private interface Node<T> {
        /** Descend from query offset {@code offset}, adding matching object ids to {@code ret}. */
        void get(TreeSearcher<T> p, BitSet ret, int offset);

        /** Collect every object id in this subtree (used when the query is exhausted). */
        void get(TreeSearcher<T> p, BitSet ret);

        /** Insert the name whose remaining codepoints start at pool offset {@code name}. */
        Node<T> put(TreeSearcher<T> p, int name, int identifier);
    }

    /**
     * A flat list of (pool offset, object id) pairs; splits into an {@link NSlice} past THRESHOLD.
     */
    private static final class NDense<T> implements Node<T> {
        final IntList data = new IntArrayList(); // [poolOff, id, ...]

        @Override
        public void get(TreeSearcher<T> p, BitSet ret, int offset) {
            boolean full = p.logic == SearcherLogic.EQUAL;
            if (!full && offset == p.acc.query().length()) {
                get(p, ret);
                return;
            }
            for (int i = 0; i < data.size() / 2; i++) {
                if (p.acc.check(offset, p.strs, data.getInt(i * 2))) {
                    ret.set(data.getInt(i * 2 + 1));
                }
            }
        }

        @Override
        public void get(TreeSearcher<T> p, BitSet ret) {
            for (int i = 0; i < data.size() / 2; i++) {
                ret.set(data.getInt(i * 2 + 1));
            }
        }

        @Override
        public Node<T> put(TreeSearcher<T> p, int name, int identifier) {
            if (data.size() >= THRESHOLD) {
                int pattern = data.getInt(0);
                Node<T> split = new NSlice<>(pattern, pattern + commonPrefix(p));
                for (int j = 0; j < data.size() / 2; j++) {
                    split.put(p, data.getInt(j * 2), data.getInt(j * 2 + 1));
                }
                split.put(p, name, identifier);
                return split;
            }
            data.add(name);
            data.add(identifier);
            return this;
        }

        /** Longest common prefix across all entries' pool positions. */
        private int commonPrefix(TreeSearcher<T> p) {
            for (int i = 0; ; i++) {
                int a = p.strs.codepoint(data.getInt(0) + i);
                for (int j = 1; j < data.size() / 2; j++) {
                    int b = p.strs.codepoint(data.getInt(j * 2) + i);
                    if (a != b || a == 0) return i;
                }
            }
        }
    }

    /** A span of shared leading codepoints (pool range [{@code start}, {@code end})). */
    private static final class NSlice<T> implements Node<T> {
        Node<T> exit = new NMap<>();
        int start;
        int end;

        NSlice(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public void get(TreeSearcher<T> p, BitSet ret, int offset) {
            get(p, ret, offset, 0);
        }

        @Override
        public void get(TreeSearcher<T> p, BitSet ret) {
            exit.get(p, ret);
        }

        private void get(TreeSearcher<T> p, BitSet ret, int offset, int step) {
            if (start + step == end) {
                exit.get(p, ret, offset);
            } else if (offset == p.acc.query().length()) {
                if (p.logic != SearcherLogic.EQUAL) exit.get(p, ret);
            } else {
                int cp = p.strs.codepoint(start + step);
                p.acc.get(cp, offset)
                        .foreach((int consumed) -> get(p, ret, offset + consumed, step + 1));
            }
        }

        @Override
        public Node<T> put(TreeSearcher<T> p, int name, int identifier) {
            int length = end - start;
            int m = p.common(start, name, length);
            if (m >= length) {
                exit = exit.put(p, name + length, identifier);
            } else {
                cut(p, start + m);
                exit = exit.put(p, name + m, identifier);
            }
            return start == end ? exit : this;
        }

        /** Split this slice at {@code offset}: the tail becomes a child of a new {@link NMap}. */
        private void cut(TreeSearcher<T> p, int offset) {
            NMap<T> insert = new NMap<>();
            int cp = p.strs.codepoint(offset);
            if (offset + 1 == end) {
                insert.putChild(cp, exit);
            } else {
                NSlice<T> half = new NSlice<>(offset + 1, end);
                half.exit = exit;
                insert.putChild(cp, half);
            }
            exit = insert;
            end = offset;
        }
    }

    /** A codepoint→child branch, plus the object ids of names that end here. */
    private static class NMap<T> implements Node<T> {
        Int2ObjectMap<Node<T>> children;
        final IntSet leaves = new IntOpenHashSet();

        @Override
        public void get(TreeSearcher<T> p, BitSet ret, int offset) {
            if (offset == p.acc.query().length()) {
                if (p.logic == SearcherLogic.EQUAL) {
                    leaves.forEach((IntConsumer) ret::set);
                } else {
                    get(p, ret);
                }
            } else if (children != null) {
                // The default entry-set iterator allocates a fresh MapEntry per child (verified in
                // the fastutil 8.5.15 bytecode); fastIterator reuses a single Entry, so a bushy
                // trie
                // does not churn Entry objects on every search.
                ObjectIterator<Int2ObjectMap.Entry<Node<T>>> it =
                        Int2ObjectMaps.fastIterator(children);
                while (it.hasNext()) {
                    Int2ObjectMap.Entry<Node<T>> e = it.next();
                    int cp = e.getIntKey();
                    Node<T> child = e.getValue();
                    p.acc.get(cp, offset)
                            .foreach((int consumed) -> child.get(p, ret, offset + consumed));
                }
            }
        }

        @Override
        public void get(TreeSearcher<T> p, BitSet ret) {
            leaves.forEach((IntConsumer) ret::set);
            if (children != null) {
                ObjectIterator<Int2ObjectMap.Entry<Node<T>>> it =
                        Int2ObjectMaps.fastIterator(children);
                while (it.hasNext()) {
                    it.next().getValue().get(p, ret);
                }
            }
        }

        @Override
        public NMap<T> put(TreeSearcher<T> p, int name, int identifier) {
            if (p.strs.end(name)) {
                leaves.add(identifier);
            } else {
                init();
                int cp = p.strs.codepoint(name);
                Node<T> sub = children.get(cp);
                if (sub == null) {
                    sub = new NDense<>();
                    children.put(cp, sub);
                }
                sub = sub.put(p, name + 1, identifier);
                children.put(cp, sub);
            }
            return this;
        }

        /** Attach a pre-built subtree under one codepoint (used by {@link NSlice#cut}). */
        void putChild(int cp, Node<T> child) {
            init();
            children.put(cp, child);
        }

        private void init() {
            if (children == null) {
                children = new Int2ObjectOpenHashMap<>();
            }
        }
    }
}
