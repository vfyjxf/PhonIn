package dev.vfyjxf.phonin.core.search;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.core.elements.CharNode;
import dev.vfyjxf.phonin.core.match.MatchContext;
import dev.vfyjxf.phonin.core.util.IndexSet;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * A {@link Searcher} that supports {@link dev.vfyjxf.phonin.PolyphoneMode#PRECISE} with trie-based
 * pruning and cross-name {@link IndexSet} caching — performance comparable to {@link TreeSearcher},
 * unlike the O(N) {@link LinearSearcher}.
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
 * <p>The trie structure is {@link CodepointTrie}; the guide branches by phonetic key and answers
 * matches from the per-key {@link #searchCache}. Flat leaves test the remaining name suffix via the
 * position-aware {@link #check(int, int)} over the key pool.
 *
 * <p>For {@link SearcherLogic#CONTAIN}, each name is inserted at every position (so the query may
 * match anywhere); for {@link SearcherLogic#BEGIN}/{@link SearcherLogic#EQUAL}, only at position 0.
 * Results are deduplicated (a CONTAIN name can be reached via several anchors) and returned in
 * insertion order.
 */
public final class PreciseSearcher<T> implements Searcher<T> {

    private static final int KEY_BASE = 0x110000; // covered-position keys start above max codepoint

    private final SearcherLogic logic;
    private final Options options;
    private final boolean partial;
    private final MatchContext context;

    //region CharNode interning: (codepoint, forcedSyllable) → phonetic key → CharNode
    private final Int2ObjectOpenHashMap<Object2IntOpenHashMap<String>> internMap =
            new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<CharNode> keyToCharNode = new Int2ObjectOpenHashMap<>();
    private int nextKey = KEY_BASE;

    //endregion
    //region name pool: phonetic keys concatenated with 0 sentinel
    final IntList keyPool = new IntArrayList();
    private final List<T> objects = new ArrayList<>();
    private final CodepointTrie trie = new CodepointTrie();

    //endregion
    //region search state (per search call)
    private String query = "";
    private final List<Int2ObjectOpenHashMap<IndexSet>> searchCache = new ArrayList<>();

    private final CodepointTrie.Guide guide =
            new CodepointTrie.Guide() {
                @Override
                public boolean exact() {
                    return logic == SearcherLogic.EQUAL;
                }

                @Override
                public int queryLength() {
                    return query.length();
                }

                @Override
                public int keyAt(int pos) {
                    return keyPool.getInt(pos);
                }

                @Override
                public boolean poolEnd(int pos) {
                    return end(pos);
                }

                @Override
                public IndexSet match(int key, int queryOff) {
                    return get(key, queryOff);
                }

                @Override
                public boolean check(int queryOff, int pos) {
                    return PreciseSearcher.this.check(queryOff, pos);
                }
            };

    public PreciseSearcher(SearcherLogic logic, Options options) {
        this.logic = logic;
        this.options = options;
        this.partial = logic.partial;
        this.context = new MatchContext(options);
    }

    //endregion
    //region indexing

    @Override
    public void put(String name, T identifier) {
        int[] cps = name.codePoints().toArray();
        String[] forced = options.polyphoneTable().segment(cps);
        int poolStart = keyPool.size();
        for (int i = 0; i < cps.length; i++) {
            keyPool.add(internKey(cps[i], forced[i]));
        }
        keyPool.add(0); // sentinel
        int id = objects.size();
        objects.add(identifier);
        int anchors = logic == SearcherLogic.CONTAIN ? Math.max(1, cps.length) : 1;
        trie.insert(guide, poolStart, anchors, id);
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
        Object2IntOpenHashMap<String> bySyllable = internMap.get(codepoint);
        if (bySyllable == null) {
            bySyllable = new Object2IntOpenHashMap<>();
            internMap.put(codepoint, bySyllable);
        } else {
            int key = bySyllable.getOrDefault(forcedNormalized, -1);
            if (key >= 0) return key;
        }
        int key = nextKey++;
        bySyllable.put(forcedNormalized, key);
        keyToCharNode.put(key, context.buildCharNode(codepoint, forcedNormalized));
        return key;
    }

    //endregion
    //region search

    @Override
    public List<T> search(String query) {
        query = options.keyboard().normalizeQuery(query);
        if (!query.equals(this.query)) {
            this.query = query;
            searchCache.clear();
        }
        BitSet ret = new BitSet(objects.size());
        trie.collect(guide, ret);
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
            return s.get(query.length() - qoff);
        }
        int v = s.value();
        int i = 0;
        while (v != 0) {
            if ((v & 0x1) == 0x1 && check(qoff + i, start + 1)) return true;
            v >>= 1;
            i++;
        }
        return false;
    }

    private boolean end(int i) {
        return i >= keyPool.size() || keyPool.getInt(i) == 0;
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
}
