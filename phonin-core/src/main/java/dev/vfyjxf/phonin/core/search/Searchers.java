package dev.vfyjxf.phonin.core.search;

import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PolyphoneMode;
import dev.vfyjxf.phonin.search.Searcher;
import dev.vfyjxf.phonin.search.SearcherLogic;

/**
 * Factories for the built-in {@link Searcher} implementations. {@link #tree} (the default via
 * {@link #best} when PRECISE is off) self-tunes: it stays as a flat {@code NDense} for small
 * indices and only builds trie structure as entries accumulate, so it is competitive with {@link
 * #simple} on small sets and superior on large ones.
 *
 * <p>When {@link PolyphoneMode#PRECISE} is active, {@link #best} returns a {@link PreciseSearcher}
 * instead — the accelerator-based searchers ({@link SimpleSearcher}/{@link TreeSearcher}) cache
 * per-codepoint {@code IndexSet}s shared across all names, which is incompatible with the
 * position-dependent restriction PRECISE applies. {@link PreciseSearcher} builds a trie on interned
 * phonetic keys (codepoint + forced syllable) with its own per-key {@code IndexSet} cache,
 * achieving trie-based pruning and cross-name memoization comparable to {@link TreeSearcher} while
 * correctly applying PRECISE disambiguation.
 */
public final class Searchers {

    private Searchers() {}

    /**
     * A linear-scan searcher with accelerator memoization; O(N) per search. Does NOT support
     * PRECISE.
     */
    public static <T> SimpleSearcher<T> simple(SearcherLogic logic, Options options) {
        return new SimpleSearcher<>(logic, options);
    }

    /**
     * A self-tuning codepoint-trie searcher. Does NOT support PRECISE.
     */
    public static <T> TreeSearcher<T> tree(SearcherLogic logic, Options options) {
        return new TreeSearcher<>(logic, options);
    }

    /**
     * A linear-scan searcher using the direct Matcher (no accelerator); supports PRECISE.
     */
    public static <T> LinearSearcher<T> linear(SearcherLogic logic, Options options) {
        return new LinearSearcher<>(logic, options);
    }

    /**
     * A trie-based searcher with phonetic-key interning; supports PRECISE with pruning + caching.
     */
    public static <T> PreciseSearcher<T> precise(SearcherLogic logic, Options options) {
        return new PreciseSearcher<>(logic, options);
    }

    /**
     * The recommended searcher for an unknown-sized index: {@link PreciseSearcher} when PRECISE is
     * active (trie-based, performance comparable to {@link TreeSearcher}), otherwise {@link
     * TreeSearcher}.
     */
    public static <T> Searcher<T> best(SearcherLogic logic, Options options) {
        if (options.polyphoneMode() == PolyphoneMode.PRECISE) {
            return new PreciseSearcher<>(logic, options);
        }
        return new TreeSearcher<>(logic, options);
    }
}
