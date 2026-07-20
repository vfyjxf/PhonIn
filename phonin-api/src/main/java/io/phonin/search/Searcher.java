package io.phonin.search;

import io.phonin.Options;
import java.util.List;

/**
 * An index over a set of names (strings), searched phonetically against one query at a time.
 * Adapted from {@code me.towdium.pinin.searchers.Searcher}; PhonIn drops PinIn's {@code context()}
 * and {@code Ticket} cache-invalidation (an immutable {@link Options} is bound at construction, so
 * there is nothing to invalidate — different options need a different Searcher).
 *
 * <p>Results are correct iff they equal the direct {@link io.phonin.match.Matcher} over the same
 * names + {@link Options} + {@link SearcherLogic}; Searchers are an optimization, not a semantic
 * change.
 *
 * <p>Not thread-safe (the trie and accelerator carry mutable, non-volatile build/search state);
 * create one per thread or synchronize external access. A single thread can interleave {@link #put}
 * and {@link #search} freely.
 */
public interface Searcher<T> {

    /** Index a name with an arbitrary caller-supplied identifier, returned by {@link #search}. */
    void put(String name, T identifier);

    /** All identifiers whose name matches the query under this searcher's logic + options. */
    List<T> search(String query);

    Options options();

    SearcherLogic logic();
}
