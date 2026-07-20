package io.phonin;

import io.phonin.match.Accelerator;
import io.phonin.match.MatchContext;
import io.phonin.match.Matcher;
import io.phonin.search.Searcher;
import io.phonin.search.SearcherLogic;
import io.phonin.search.Searchers;

/**
 * Entry point. A {@code PhonIn} instance carries a reference to the dataset loader; every match
 * call takes an explicit {@link Options}, so one instance serves all systems / modes / fuzzy
 * combinations.
 *
 * <p>Data is loaded on demand: calling {@link #create()} creates an empty {@link PhonIn} and the
 * system required by an {@link Options} is loaded the first time it is used. Use {@link
 * #createAll()} to eagerly load all bundled systems, or {@link #create(PhoneticSystem...)} / {@link
 * #create(PhonInData)} to control exactly what is loaded up front.
 */
public final class PhonIn {

    private final PhonInData data;
    // Reuse the per-call MatchContext for the common case: same Options used repeatedly in a
    // thread. This avoids re-building CharNodes and re-allocating the cache on every direct call.
    private final ThreadLocal<MatchContext> directContext = new ThreadLocal<>();

    /** Empty loader: systems are loaded lazily on first use. */
    public PhonIn() {
        this(PhonInData.empty());
    }

    /** Load only the specified bundled systems eagerly. */
    public PhonIn(PhoneticSystem... systems) {
        this(PhonInData.empty());
        data.load(systems);
    }

    public PhonIn(PhonInData data) {
        this.data = data;
    }

    private MatchContext contextFor(Options options) {
        MatchContext ctx = directContext.get();
        if (ctx == null || !ctx.options().equals(options)) {
            ctx = new MatchContext(options);
            directContext.set(ctx);
        }
        return ctx;
    }

    /** Create an empty PhonIn; data is loaded on first call. */
    public static PhonIn create() {
        return new PhonIn();
    }

    /** Eagerly load all bundled systems. */
    public static PhonIn createAll() {
        return new PhonIn(PhonInData.loadFromClasspath());
    }

    /** Eagerly load only the specified bundled systems. */
    public static PhonIn create(PhoneticSystem... systems) {
        return new PhonIn(systems);
    }

    public static PhonIn create(PhonInData data) {
        return new PhonIn(data);
    }

    public boolean contains(String text, String query, Options options) {
        data.loadForOptions(options);
        return Matcher.contains(Codepoints.toCodepoints(text), query, contextFor(options));
    }

    public boolean begins(String text, String query, Options options) {
        data.loadForOptions(options);
        return Matcher.begins(Codepoints.toCodepoints(text), query, contextFor(options));
    }

    public boolean matches(String text, String query, Options options) {
        data.loadForOptions(options);
        return Matcher.matches(Codepoints.toCodepoints(text), query, contextFor(options));
    }

    /**
     * Pre-compile a query for the stable-call pattern (one query, many texts). The returned {@link
     * AcceleratedQuery} caches the per-codepoint phoneme matches for the query and reuses them
     * across every {@code contains/begins/matches} call — faster than the per-call direct matchers
     * when the same query is tested against many texts. Results are identical to the direct
     * matchers for the same {@link Options}.
     */
    public AcceleratedQuery compile(String query, Options options) {
        data.loadForOptions(options);
        Accelerator acc = new Accelerator(options, true);
        acc.search(query);
        return new AcceleratedQuery(acc);
    }

    public boolean contains(String text, AcceleratedQuery q) {
        return q.contains(text);
    }

    public boolean begins(String text, AcceleratedQuery q) {
        return q.begins(text);
    }

    public boolean matches(String text, AcceleratedQuery q) {
        return q.matches(text);
    }

    /**
     * Create a {@link Searcher} for batch search (one query against many indexed names). The
     * returned searcher self-tunes to the index size; use {@link Searchers} directly to pick a
     * specific implementation.
     */
    public <T> Searcher<T> searcher(SearcherLogic logic, Options options) {
        data.loadForOptions(options);
        return Searchers.best(logic, options);
    }

    public PhonInData data() {
        return data;
    }
}
