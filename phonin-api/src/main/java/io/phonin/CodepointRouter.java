package io.phonin;

import java.util.Collections;

/**
 * Routes a codepoint to the {@link PhoneticSystem} it should be matched under, so a single match
 * call can handle mixed-script text (e.g. Mandarin Han next to Korean Hangul). In single-system
 * mode (the default, {@link Options#builder(PhoneticSystem)}) the router is unused; in MULTI mode
 * ({@link Options#multi(CodepointRouter)}) each text codepoint is matched by the system this router
 * returns for it, or — when it returns {@code null} — as a literal char (the codepoint itself).
 *
 * <p>The bundled block router lives in the {@code phonin-systems} module ({@code ByBlockRouter}).
 * It maps Hangul &rarr; Korean, kana &rarr; Japanese, Bopomofo &rarr; Zhuyin, CJK Han &rarr; a
 * caller-chosen {@code hanDefault}, everything else &rarr; {@code null}. Implement this interface
 * for custom routing.
 */
public interface CodepointRouter {

    /**
     * The system to match {@code codepoint} under, or {@code null} to match it as a literal char.
     */
    PhoneticSystem systemFor(int codepoint);

    /**
     * The systems this router may return. Used for on-demand data loading. Custom routers that
     * return an empty iterable will cause PhonIn to load all bundled systems defensively.
     */
    default Iterable<PhoneticSystem> knownSystems() {
        return Collections.emptyList();
    }
}
