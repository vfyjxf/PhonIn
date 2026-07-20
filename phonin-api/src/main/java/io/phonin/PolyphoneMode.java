package io.phonin;

/**
 * Whether context-aware polyphone disambiguation is active.
 *
 * <p>{@link #OFF} (the default) preserves today's behaviour: a polyphone matches if <em>any</em> of
 * its readings satisfies the query (so {@code contains("银行","yinxing")} is {@code true} — 行 accepts
 * its "xing" reading even though the word 银行 forces "hang").
 *
 * <p>{@link #PRECISE} tightens that: a {@link PolyphoneTable} supplies per-word, per-position
 * readings (e.g. 银行 → yin2,hang2); the engine segments the text once per call and, for a char
 * covered by a recognized word, restricts that char to the word's reading for its position
 * (intersected with the char's own readings by toneless syllable; falls back to all readings if
 * there is no intersection — never over-restricts). PRECISE is monotonic: it only ever removes
 * wrong-context polyphone matches, never breaks a match that succeeded before.
 *
 * <p>PRECISE works on the direct API ({@link PhonIn#contains}/{@link PhonIn#begins}/{@link
 * PhonIn#matches}). It is rejected on the accelerator-based paths ({@link PhonIn#compile} / {@link
 * io.phonin.search.Searcher}): their speed rests on {@link io.phonin.match.Accelerator} caching
 * {@code IndexSet}s per codepoint shared across all names, which a name-and-position-dependent
 * restriction breaks. A non-accelerated precise {@code Searcher} is a documented follow-up.
 */
public enum PolyphoneMode {
    OFF,
    PRECISE
}
