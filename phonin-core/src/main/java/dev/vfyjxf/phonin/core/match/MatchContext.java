package dev.vfyjxf.phonin.core.match;

import dev.vfyjxf.phonin.CodepointRouter;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.PolyphoneMode;
import dev.vfyjxf.phonin.PolyphoneTable;
import dev.vfyjxf.phonin.core.elements.CharNode;
import dev.vfyjxf.phonin.core.elements.CharNodes;
import dev.vfyjxf.phonin.model.CharEntry;
import dev.vfyjxf.phonin.model.Reading;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * A per-call matching frame: an {@link Options} snapshot plus a small CharNode cache for the text
 * being matched. In single-system mode every codepoint is matched by {@link Options#system()}; in
 * MULTI mode each codepoint is routed by {@link Options#router()} to the system that claims it. The
 * matcher itself stays system-blind. Created fresh for each {@code PhonIn.contains/begins/matches}
 * call.
 */
public final class MatchContext {

    final Options options;
    final PhoneticSystem system; // non-null in single-system mode; null in MULTI
    final CodepointRouter router; // non-null in MULTI; null in single-system
    final boolean sequence;

    private final Int2ObjectOpenHashMap<CharNode> cache = new Int2ObjectOpenHashMap<>();

    // PRECISE-only: the per-position forced readings for the current text, lazily segmented once.
    private String[] polyphoneSegments;
    private int polyphoneTextKey = 0; // identity (System.identityHashCode) of the segmented text

    public MatchContext(Options options) {
        this.options = options;
        this.system = options.system();
        this.router = options.router();
        this.sequence = options.sequence();
    }

    public Options options() {
        return options;
    }

    /**
     * Build (and cache) the CharNode for one codepoint within this call's option set.
     */
    public CharNode charNode(int codepoint) {
        CharNode cached = cache.get(codepoint);
        if (cached != null) return cached;

        PhoneticSystem sys = router != null ? router.systemFor(codepoint) : system;
        CharEntry entry = sys == null ? null : sys.charEntry(codepoint);
        CharNode node = CharNodes.fromEntry(codepoint, entry, options);
        cache.put(codepoint, node);
        return node;
    }

    /**
     * Position-aware {@link CharNode} lookup, used by the direct matcher. In {@link
     * PolyphoneMode#OFF} (the default) this delegates to the cached codepoint path — zero behaviour
     * change. In {@link PolyphoneMode#PRECISE} the text is segmented once (lazily, cached on this
     * context keyed by the text identity); a position covered by a recognized word restricts the
     * char to that word's reading for the position (intersected with the char's own readings by
     * toneless syllable; falls back to all readings if there is no intersection — never
     * over-restricts). Covered positions bypass the codepoint cache: the same codepoint can map to
     * different readings at different positions in one text (e.g. 银行一行).
     */
    public CharNode charNode(int[] text, int position) {
        if (options.polyphoneMode() != PolyphoneMode.PRECISE) {
            return charNode(text[position]);
        }
        String forced = forcedReading(text, position);
        if (forced == null) {
            return charNode(text[position]); // uncovered: today's "any reading" behaviour
        }
        PhoneticSystem sys = system; // PRECISE is single-system (validated by Options.build)
        CharEntry entry = sys == null ? null : sys.charEntry(text[position]);
        if (entry == null) {
            return charNode(text[position]); // no dataset entry: literal fallback
        }
        Reading chosen = pickReading(entry, forced);
        if (chosen == null) {
            return charNode(text[position]); // no intersection -> fall back to all readings
        }
        return CharNodes.fromReadings(text[position], new Reading[] {chosen}, options);
    }

    /**
     * The forced normalized reading for {@code position}, segmenting {@code text} once.
     */
    private String forcedReading(int[] text, int position) {
        if (polyphoneSegments == null || polyphoneTextKey != System.identityHashCode(text)) {
            PolyphoneTable table = options.polyphoneTable();
            polyphoneSegments = table.segment(text);
            polyphoneTextKey = System.identityHashCode(text);
        }
        return polyphoneSegments[position];
    }

    /**
     * Find the char reading whose toneless syllable equals the toneless form of the forced reading.
     * Disambiguation is about <em>which</em> reading (xing vs hang); tone disagreement between the
     * model and the char table is tolerated, and the tone policy still applies to the chosen
     * reading's normalized form downstream.
     */
    private Reading pickReading(CharEntry entry, String forced) {
        PhoneticSystem sys = system;
        String forcedSyllable = sys.toneConvention().strip(forced);
        for (Reading r : entry.readingsArray()) {
            if (r.syllable.equals(forcedSyllable)) return r;
        }
        return null;
    }

    /**
     * Build a CharNode for {@code codepoint}, optionally restricted to a forced reading. When
     * {@code forcedNormalized} is {@code null} (or no matching reading exists), returns the
     * all-readings CharNode (identical to {@link #charNode(int)}). When non-null, restricts to the
     * single reading whose toneless syllable matches. Engine-internal: used by {@link
     * dev.vfyjxf.phonin.core.search.PreciseSearcher} to pre-compute per-position CharNodes at index
     * time.
     */
    public CharNode buildCharNode(int codepoint, String forcedNormalized) {
        PhoneticSystem sys = router != null ? router.systemFor(codepoint) : system;
        CharEntry entry = sys == null ? null : sys.charEntry(codepoint);
        if (entry == null) return CharNode.literal(codepoint);
        if (forcedNormalized == null) return CharNodes.fromEntry(codepoint, entry, options);
        Reading chosen = pickReading(entry, forcedNormalized);
        if (chosen == null) return CharNodes.fromEntry(codepoint, entry, options);
        return CharNodes.fromReadings(codepoint, new Reading[] {chosen}, options);
    }
}
