package dev.vfyjxf.phonin.core;

import dev.vfyjxf.phonin.Codepoints;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.core.match.Accelerator;

/**
 * A query pre-compiled for the "many texts, one query" pattern (live filtering a list as the user
 * types): the phoneme-match cache is built once for the query and reused across every text tested
 * with {@link #contains(String)} / {@link #begins(String)} / {@link #matches(String)}. Created by
 * {@link PhonIn#compile(String, Options)}.
 *
 * <p>Every method's result is identical to the equivalent direct call on {@link PhonIn} with the
 * same {@link Options} — the accelerator only caches, it does not change semantics. Switching
 * between {@code contains}/{@code begins} (partial) and {@code matches} (exact) rebuilds the cache,
 * so prefer one mode per compiled query (the typical stable-call usage).
 */
public final class AcceleratedQuery {

    private final Accelerator acc;

    AcceleratedQuery(Accelerator acc) {
        this.acc = acc;
    }

    public Options options() {
        return acc.options();
    }

    public String query() {
        return acc.query();
    }

    public boolean contains(String text) {
        acc.partial(true);
        return acc.contains(Codepoints.toCodepoints(text));
    }

    public boolean begins(String text) {
        acc.partial(true);
        return acc.begins(Codepoints.toCodepoints(text));
    }

    public boolean matches(String text) {
        acc.partial(false);
        return acc.matches(Codepoints.toCodepoints(text));
    }
}
