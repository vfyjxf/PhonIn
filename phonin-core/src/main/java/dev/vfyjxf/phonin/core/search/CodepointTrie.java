package dev.vfyjxf.phonin.core.search;

import dev.vfyjxf.phonin.core.util.IndexSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.BitSet;

/**
 * A self-tuning trie over pool-resident key sequences, shared by {@link TreeSearcher} (keys are
 * codepoints) and {@link PreciseSearcher} (keys are interned phonetic keys). Ported from {@code
 * me.towdium.pinin.searchers.TreeSearcher}'s {@code NDense}→{@code NSlice}→{@code NMap}
 * progression: a flat pair list ({@link NDense}) splits into a shared-prefix span ({@link NSlice})
 * past {@link #THRESHOLD}, and a span dispatches to a key→child branch ({@link NMap}) at the
 * divergence point.
 *
 * <p>The host supplies everything the trie cannot know through {@link Guide}: how to read a key
 * from the pool, how to match a key against the query (cached per key+offset), and how to test a
 * flat leaf's remaining suffix.
 */
final class CodepointTrie {

    /**
     * A node splits from flat to trie-form once it holds this many entries.
     */
    static final int THRESHOLD = 128;

    /**
     * Host callbacks the trie descends and inserts through.
     */
    interface Guide {
        /**
         * EQUAL logic: no partial tail when the query is exhausted.
         */
        boolean exact();

        int queryLength();

        /**
         * The key at pool position {@code pos} (valid only when {@link #poolEnd} is false).
         */
        int keyAt(int pos);

        /**
         * Pool position {@code pos} is the sentinel or beyond.
         */
        boolean poolEnd(int pos);

        /**
         * The (cached) query-lengths {@code key} can consume at {@code queryOff}.
         */
        IndexSet match(int key, int queryOff);

        /**
         * Test the whole remaining suffix starting at pool position {@code pos} against the query
         * from {@code queryOff} (flat-leaf path).
         */
        boolean check(int queryOff, int pos);
    }

    private Node root = new NDense();

    /**
     * Insert the id at every pool position in [{@code poolStart}, {@code poolStart + anchors}).
     */
    void insert(Guide g, int poolStart, int anchors, int id) {
        for (int i = 0; i < anchors; i++) {
            root = root.put(g, poolStart + i, id);
        }
    }

    /**
     * Descend from query offset 0, setting matching ids in {@code out}.
     */
    void collect(Guide g, BitSet out) {
        root.get(g, out, 0);
    }

    /**
     * Longest common prefix length (in pool positions) between two sequences, capped at {@code
     * max}.
     */
    private static int common(Guide g, int s1, int s2, int max) {
        for (int i = 0; i < max; i++) {
            int a = g.keyAt(s1 + i);
            int b = g.keyAt(s2 + i);
            if (a != b || a == 0) return i;
        }
        return max;
    }

    private interface Node {
        /**
         * Descend from query offset {@code offset}, adding matching ids to {@code ret}.
         */
        void get(Guide g, BitSet ret, int offset);

        /**
         * Collect every id in this subtree (used when the query is exhausted).
         */
        void getAll(Guide g, BitSet ret);

        /**
         * Insert the id whose remaining keys start at pool position {@code name}.
         */
        Node put(Guide g, int name, int id);
    }

    /**
     * A flat list of (pool position, id) pairs; splits into an {@link NSlice} past THRESHOLD.
     */
    private static final class NDense implements Node {
        final IntList data = new IntArrayList(); // [poolPos, id, ...]

        @Override
        public void get(Guide g, BitSet ret, int offset) {
            if (!g.exact() && offset == g.queryLength()) {
                getAll(g, ret);
                return;
            }
            for (int i = 0; i < data.size() / 2; i++) {
                if (g.check(offset, data.getInt(i * 2))) {
                    ret.set(data.getInt(i * 2 + 1));
                }
            }
        }

        @Override
        public void getAll(Guide g, BitSet ret) {
            for (int i = 0; i < data.size() / 2; i++) {
                ret.set(data.getInt(i * 2 + 1));
            }
        }

        @Override
        public Node put(Guide g, int name, int id) {
            if (data.size() >= THRESHOLD) {
                int pattern = data.getInt(0);
                Node split = new NSlice(pattern, pattern + commonPrefix(g));
                for (int j = 0; j < data.size() / 2; j++) {
                    split.put(g, data.getInt(j * 2), data.getInt(j * 2 + 1));
                }
                split.put(g, name, id);
                return split;
            }
            data.add(name);
            data.add(id);
            return this;
        }

        /**
         * Longest common prefix across all entries' pool positions.
         */
        private int commonPrefix(Guide g) {
            for (int i = 0; ; i++) {
                int a = g.keyAt(data.getInt(0) + i);
                for (int j = 1; j < data.size() / 2; j++) {
                    int b = g.keyAt(data.getInt(j * 2) + i);
                    if (a != b || a == 0) return i;
                }
            }
        }
    }

    /**
     * A span of shared leading keys (pool range [{@code start}, {@code end})).
     */
    private static final class NSlice implements Node {
        Node exit = new NMap();
        int start;
        int end;

        NSlice(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public void get(Guide g, BitSet ret, int offset) {
            get(g, ret, offset, 0);
        }

        @Override
        public void getAll(Guide g, BitSet ret) {
            exit.getAll(g, ret);
        }

        private void get(Guide g, BitSet ret, int offset, int step) {
            if (start + step == end) {
                exit.get(g, ret, offset);
            } else if (offset == g.queryLength()) {
                if (!g.exact()) exit.getAll(g, ret);
            } else {
                int v = g.match(g.keyAt(start + step), offset).value();
                int i = 0;
                while (v != 0) {
                    if ((v & 0x1) == 0x1) get(g, ret, offset + i, step + 1);
                    v >>= 1;
                    i++;
                }
            }
        }

        @Override
        public Node put(Guide g, int name, int id) {
            int length = end - start;
            int m = common(g, start, name, length);
            if (m >= length) {
                exit = exit.put(g, name + length, id);
            } else {
                cut(g, start + m);
                exit = exit.put(g, name + m, id);
            }
            return start == end ? exit : this;
        }

        /**
         * Split this slice at {@code offset}: the tail becomes a child of a new {@link NMap}.
         */
        private void cut(Guide g, int offset) {
            NMap insert = new NMap();
            int key = g.keyAt(offset);
            if (offset + 1 == end) {
                insert.putChild(key, exit);
            } else {
                NSlice half = new NSlice(offset + 1, end);
                half.exit = exit;
                insert.putChild(key, half);
            }
            exit = insert;
            end = offset;
        }
    }

    /**
     * A key→child branch, plus the ids of sequences that end here.
     */
    private static final class NMap implements Node {
        Int2ObjectMap<Node> children;
        final IntSet leaves = new IntOpenHashSet();

        @Override
        public void get(Guide g, BitSet ret, int offset) {
            if (offset == g.queryLength()) {
                if (g.exact()) {
                    addLeaves(ret);
                } else {
                    getAll(g, ret);
                }
            } else if (children != null) {
                // The default entry-set iterator allocates a fresh MapEntry per child (verified in
                // the fastutil 8.5.15 bytecode); fastIterator reuses a single Entry, so a bushy
                // trie does not churn Entry objects on every search.
                ObjectIterator<Int2ObjectMap.Entry<Node>> it =
                        Int2ObjectMaps.fastIterator(children);
                while (it.hasNext()) {
                    Int2ObjectMap.Entry<Node> e = it.next();
                    Node child = e.getValue();
                    int v = g.match(e.getIntKey(), offset).value();
                    int i = 0;
                    while (v != 0) {
                        if ((v & 0x1) == 0x1) child.get(g, ret, offset + i);
                        v >>= 1;
                        i++;
                    }
                }
            }
        }

        @Override
        public void getAll(Guide g, BitSet ret) {
            addLeaves(ret);
            if (children != null) {
                ObjectIterator<Int2ObjectMap.Entry<Node>> it =
                        Int2ObjectMaps.fastIterator(children);
                while (it.hasNext()) {
                    it.next().getValue().getAll(g, ret);
                }
            }
        }

        @Override
        public NMap put(Guide g, int name, int id) {
            if (g.poolEnd(name)) {
                leaves.add(id);
            } else {
                init();
                int key = g.keyAt(name);
                Node sub = children.get(key);
                if (sub == null) {
                    sub = new NDense();
                    children.put(key, sub);
                }
                sub = sub.put(g, name + 1, id);
                children.put(key, sub);
            }
            return this;
        }

        /**
         * Attach a pre-built subtree under one key (used by {@link NSlice#cut}).
         */
        void putChild(int key, Node child) {
            init();
            children.put(key, child);
        }

        private void addLeaves(BitSet ret) {
            IntIterator it = leaves.iterator();
            while (it.hasNext()) ret.set(it.nextInt());
        }

        private void init() {
            if (children == null) {
                children = new Int2ObjectOpenHashMap<>();
            }
        }
    }
}
