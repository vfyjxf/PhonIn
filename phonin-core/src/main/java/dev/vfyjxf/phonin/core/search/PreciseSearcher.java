package dev.vfyjxf.phonin.core.search;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PolyphoneMode;
import dev.vfyjxf.phonin.PolyphoneTable;
import dev.vfyjxf.phonin.core.elements.CharNode;
import dev.vfyjxf.phonin.core.match.MatchContext;
import dev.vfyjxf.phonin.core.util.IndexSet;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * A {@link Searcher} that supports {@link PolyphoneMode#PRECISE} with trie-based pruning and
 * cross-name {@link IndexSet} caching — performance comparable to {@link TreeSearcher}, unlike the
 * O(N) {@link LinearSearcher}.
 *
 * <p>The core idea: pre-segment each name at index time and assign each position a <b>phonetic
 * key</b> — an int that uniquely identifies the {@link CharNode} for that position. For
 * non-polyphone chars (the vast majority) the key is just the codepoint (identical to {@link
 * TreeSearcher}'s trie). For polyphone chars covered by a table word, the key encodes both the
 * codepoint and the forced syllable, so two names with the same codepoint but different forced
 * readings branch differently in the trie. CharNodes are <b>interned</b>: the same (codepoint,
 * forcedSyllable) pair always maps to the same CharNode instance, so the search-time IndexSet cache
 * (keyed by phonetic key + query offset) is shared across all names with the same forced reading at
 * that position.
 *
 * <p>The trie structure mirrors {@link TreeSearcher}'s ({@link NDense}→{@link NSlice}→{@link
 * NMap}), but branches by phonetic key instead of raw codepoint, and uses a phonetic-key-aware
 * cache instead of {@link dev.vfyjxf.phonin.core.match.Accelerator}. Search descends the trie guided by
 * cached {@link CharNode#match}; flat {@link NDense} leaves test the remaining name suffix via a
 * position-aware {@link #check(int, int, int)} that reads pre-computed CharNodes from the key pool.
 *
 * <p>For {@link SearcherLogic#CONTAIN}, each name is inserted at every position (so the query may
 * match anywhere); for {@link SearcherLogic#BEGIN}/{@link SearcherLogic#EQUAL}, only at position 0.
 * Results are deduplicated (a CONTAIN name can be reached via several anchors) and returned in
 * insertion order.
 */
public final class PreciseSearcher<T> implements Searcher<T> {

    private static final int THRESHOLD = 128;
    private static final int KEY_BASE = 0x110000; // covered-position keys start above max codepoint

    private final SearcherLogic logic;
    private final Options options;
    private final boolean partial;
    private final PolyphoneTable table;
    private final MatchContext context;

    //region CharNode interning: (codepoint, forcedSyllable) → phonetic key → CharNode
    private final Map<String, Integer> internMap = new HashMap<>();
    private final Int2ObjectMap<CharNode> keyToCharNode = new Int2ObjectOpenHashMap<>();
    private int nextKey = KEY_BASE;

    //endregion
    //region name pool: phonetic keys concatenated with 0 sentinel
    final IntList keyPool = new IntArrayList();
    private final List<T> objects = new ArrayList<>();

    //endregion
    //region trie
    private Node<T> root = new NDense<>();

    //endregion
    //region search state (per search call)
    private String query;
    private final List<Int2ObjectOpenHashMap<IndexSet>> searchCache = new ArrayList<>();

    public PreciseSearcher(SearcherLogic logic, Options options) {
        this.logic = logic;
        this.options = options;
        this.partial = logic.partial;
        this.table = options.polyphoneTable();
        this.context = new MatchContext(options);
    }

    //endregion
    //region indexing

    @Override
    public void put(String name, T identifier) {
        int[] cps = name.codePoints().toArray();
        String[] forced = table.segment(cps);
        int[] keys = new int[cps.length];
        for (int i = 0; i < cps.length; i++) {
            keys[i] = internKey(cps[i], forced[i]);
        }
        int poolStart = keyPool.size();
        for (int key : keys) {
            keyPool.add(key);
        }
        keyPool.add(0); // sentinel
        int id = objects.size();
        objects.add(identifier);
        int end = logic == SearcherLogic.CONTAIN ? Math.max(1, keys.length) : 1;
        for (int i = 0; i < end; i++) {
            root = root.put(this, poolStart + i, id);
        }
    }

    /**
     * Assign (or reuse) a phonetic key for (codepoint, forcedNormalized).
     */
    private int internKey(int codepoint, String forcedNormalized) {
        if (forcedNormalized == null) {
            if (!keyToCharNode.containsKey(codepoint)) {
                keyToCharNode.put(codepoint, context.buildCharNode(codepoint, null));
            }
            return codepoint;
        }
        String mapKey = codepoint + "\t" + forcedNormalized;
        Integer existing = internMap.get(mapKey);
        if (existing != null) return existing;
        int key = nextKey++;
        internMap.put(mapKey, key);
        keyToCharNode.put(key, context.buildCharNode(codepoint, forcedNormalized));
        return key;
    }

    //endregion
    //region search

    @Override
    public List<T> search(String query) {
        this.query = query;
        searchCache.clear();
        BitSet ret = new BitSet(objects.size());
        root.get(this, ret, 0);
        List<T> out = new ArrayList<>(ret.cardinality());
        for (int i = ret.nextSetBit(0); i >= 0; i = ret.nextSetBit(i + 1)) {
            out.add(objects.get(i));
        }
        return out;
    }

    /**
     * Cached IndexSet for phonetic {@code key} at query {@code offset}.
     */
    IndexSet get(int key, int offset) {
        while (searchCache.size() <= offset) searchCache.add(null);
        Int2ObjectOpenHashMap<IndexSet> layer = searchCache.get(offset);
        if (layer == null) {
            layer = new Int2ObjectOpenHashMap<>();
            searchCache.set(offset, layer);
        }
        IndexSet cached = layer.get(key);
        if (cached != null) return cached;
        CharNode node = keyToCharNode.get(key);
        IndexSet result = node.match(query, offset, partial);
        layer.put(key, result);
        return result;
    }

    /**
     * Walk the remaining name suffix from pool offset {@code start} against query from {@code
     * qoff}.
     */
    boolean check(int qoff, int start) {
        if (qoff == query.length()) return partial || end(start);
        if (end(start)) return false;
        IndexSet s = get(keyPool.getInt(start), qoff);
        if (end(start + 1)) {
            int need = query.length() - qoff;
            return s.get(need);
        }
        final int nextStart = start + 1;
        return s.traverse(i -> check(qoff + i, nextStart));
    }

    private boolean end(int i) {
        return i >= keyPool.size() || keyPool.getInt(i) == 0;
    }

    private int common(int s1, int s2, int max) {
        for (int i = 0; i < max; i++) {
            int a = keyPool.getInt(s1 + i);
            int b = keyPool.getInt(s2 + i);
            if (a != b || a == 0) return i;
        }
        return max;
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

    //endregion
    //region trie nodes (mirror TreeSearcher's NDense/NSlice/NMap)

    private interface Node<T> {
        void get(PreciseSearcher<T> p, BitSet ret, int offset);

        void get(PreciseSearcher<T> p, BitSet ret);

        Node<T> put(PreciseSearcher<T> p, int name, int identifier);
    }

    private static final class NDense<T> implements Node<T> {
        final IntList data = new IntArrayList(); // [poolOff, id, ...]

        @Override
        public void get(PreciseSearcher<T> p, BitSet ret, int offset) {
            boolean full = p.logic == SearcherLogic.EQUAL;
            if (!full && offset == p.query.length()) {
                get(p, ret);
                return;
            }
            for (int i = 0; i < data.size() / 2; i++) {
                if (p.check(offset, data.getInt(i * 2))) {
                    ret.set(data.getInt(i * 2 + 1));
                }
            }
        }

        @Override
        public void get(PreciseSearcher<T> p, BitSet ret) {
            for (int i = 0; i < data.size() / 2; i++) {
                ret.set(data.getInt(i * 2 + 1));
            }
        }

        @Override
        public Node<T> put(PreciseSearcher<T> p, int name, int identifier) {
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

        private int commonPrefix(PreciseSearcher<T> p) {
            int size = data.size() / 2;
            for (int i = 0; ; i++) {
                int a = p.keyPool.getInt(data.getInt(0) + i);
                for (int j = 1; j < size; j++) {
                    int b = p.keyPool.getInt(data.getInt(j * 2) + i);
                    if (a != b || a == 0) return i;
                }
            }
        }
    }

    private static final class NSlice<T> implements Node<T> {
        Node<T> exit = new NMap<>();
        int start;
        int end;

        NSlice(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public void get(PreciseSearcher<T> p, BitSet ret, int offset) {
            get(p, ret, offset, 0);
        }

        @Override
        public void get(PreciseSearcher<T> p, BitSet ret) {
            exit.get(p, ret);
        }

        private void get(PreciseSearcher<T> p, BitSet ret, int offset, int step) {
            if (start + step == end) {
                exit.get(p, ret, offset);
            } else if (offset == p.query.length()) {
                if (p.logic != SearcherLogic.EQUAL) exit.get(p, ret);
            } else {
                int key = p.keyPool.getInt(start + step);
                p.get(key, offset)
                        .foreach((int consumed) -> get(p, ret, offset + consumed, step + 1));
            }
        }

        @Override
        public Node<T> put(PreciseSearcher<T> p, int name, int identifier) {
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

        private void cut(PreciseSearcher<T> p, int offset) {
            NMap<T> insert = new NMap<>();
            int key = p.keyPool.getInt(offset);
            if (offset + 1 == end) {
                insert.putChild(key, exit);
            } else {
                NSlice<T> half = new NSlice<>(offset + 1, end);
                half.exit = exit;
                insert.putChild(key, half);
            }
            exit = insert;
            end = offset;
        }
    }

    private static class NMap<T> implements Node<T> {
        Int2ObjectMap<Node<T>> children;
        final IntSet leaves = new IntOpenHashSet();

        @Override
        public void get(PreciseSearcher<T> p, BitSet ret, int offset) {
            if (offset == p.query.length()) {
                if (p.logic == SearcherLogic.EQUAL) {
                    leaves.forEach((IntConsumer) ret::set);
                } else {
                    get(p, ret);
                }
            } else if (children != null) {
                ObjectIterator<Int2ObjectMap.Entry<Node<T>>> it =
                        Int2ObjectMaps.fastIterator(children);
                while (it.hasNext()) {
                    Int2ObjectMap.Entry<Node<T>> e = it.next();
                    int key = e.getIntKey();
                    Node<T> child = e.getValue();
                    p.get(key, offset)
                            .foreach((int consumed) -> child.get(p, ret, offset + consumed));
                }
            }
        }

        @Override
        public void get(PreciseSearcher<T> p, BitSet ret) {
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
        public NMap<T> put(PreciseSearcher<T> p, int name, int identifier) {
            if (p.end(name)) {
                leaves.add(identifier);
            } else {
                init();
                int key = p.keyPool.getInt(name);
                Node<T> sub = children.get(key);
                if (sub == null) {
                    sub = new NDense<>();
                    children.put(key, sub);
                }
                sub = sub.put(p, name + 1, identifier);
                children.put(key, sub);
            }
            return this;
        }

        void putChild(int key, Node<T> child) {
            init();
            children.put(key, child);
        }

        private void init() {
            if (children == null) {
                children = new Int2ObjectOpenHashMap<>();
            }
        }
    }
    //endregion
}
