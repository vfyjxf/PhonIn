package dev.vfyjxf.phonin;

import dev.vfyjxf.phonin.fuzzy.FuzzyRule;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable, per-call matching configuration. One {@link PhonIn} instance serves every system /
 * mode / fuzzy combination; there is no global mutation. Pass the relevant {@code Options} to each
 * {@code contains/begins/matches} call.
 *
 * <p>Two regimes: <b>single-system</b> (built via {@link #builder(PhoneticSystem)} — the common
 * case, every codepoint matched by that one system) and <b>MULTI</b> (built via {@link
 * #multi(CodepointRouter)} — each codepoint routed to a system by block, for mixed-script text).
 * Exactly one of {@link #system()} / {@link #router()} is non-null.
 */
public final class Options {

    private final PhoneticSystem system; // non-null in single-system mode
    private final CodepointRouter router; // non-null in MULTI mode
    private final Keyboard keyboard;
    private final MatchMode mode;
    private final TonePolicy tone;
    private final AbbrevPolicy abbrev;
    private final Set<FuzzyRule> fuzzies;
    private final PolyphoneMode polyphoneMode;
    private final PolyphoneTable polyphoneTable;

    private Options(Builder b) {
        this.system = b.system;
        this.router = b.router;
        this.keyboard = b.keyboard;
        this.mode = b.mode;
        this.tone = b.tone;
        this.abbrev = b.abbrev;
        this.fuzzies =
                b.fuzzies.isEmpty()
                        ? Collections.<FuzzyRule>emptySet()
                        : Collections.unmodifiableSet(new LinkedHashSet<>(b.fuzzies));
        this.polyphoneMode = b.polyphoneMode;
        this.polyphoneTable = b.polyphoneTable;
    }

    /**
     * The single system; {@code null} in MULTI mode (then {@link #router()} is set).
     */
    public PhoneticSystem system() {
        return system;
    }

    /**
     * The codepoint router; {@code null} in single-system mode.
     */
    public CodepointRouter router() {
        return router;
    }

    /**
     * How the system's toneless syllables are re-mapped for matching (identity by default).
     */
    public Keyboard keyboard() {
        return keyboard;
    }

    public MatchMode mode() {
        return mode;
    }

    public TonePolicy tone() {
        return tone;
    }

    public AbbrevPolicy abbrev() {
        return abbrev;
    }

    public Set<FuzzyRule> fuzzies() {
        return fuzzies;
    }

    /**
     * Whether context-aware polyphone disambiguation is active ({@link PolyphoneMode#OFF} by
     * default).
     */
    public PolyphoneMode polyphoneMode() {
        return polyphoneMode;
    }

    /**
     * The polyphone table consulted when {@link #polyphoneMode()} is {@link PolyphoneMode#PRECISE}.
     */
    public PolyphoneTable polyphoneTable() {
        return polyphoneTable;
    }

    /**
     * Abbreviation (简拼) is active when requested AND supported by the system. Under the identity
     * keyboard the abbreviation is the pinyin initial (中→{@code zh}); under a non-identity
     * keyboard it is the first key of the encoded surface (shuangpin 中→{@code vs}→{@code v}).
     * Undefined in MULTI mode (no single system), so disabled there.
     */
    public boolean sequence() {
        return router == null && abbrev == AbbrevPolicy.INITIALS && system.abbreviable();
    }

    /**
     * System default fuzzies merged with the explicitly requested ones; MULTI uses only the latter.
     */
    public Set<FuzzyRule> effectiveFuzzies() {
        if (router != null) return fuzzies; // MULTI: no single system default
        Set<FuzzyRule> def = system.defaultFuzzies();
        if (def.isEmpty()) return fuzzies;
        if (fuzzies.isEmpty()) return def;
        LinkedHashSet<FuzzyRule> all = new LinkedHashSet<>(def);
        all.addAll(fuzzies);
        return Collections.unmodifiableSet(all);
    }

    public static Builder builder(PhoneticSystem system) {
        return new Builder(system);
    }

    /**
     * Build a MULTI-mode Options that routes each codepoint to a system via {@code router}.
     */
    public static Options multi(CodepointRouter router) {
        if (router == null) throw new IllegalArgumentException("router required for MULTI mode");
        Builder b = new Builder();
        b.router = router;
        return b.build();
    }

    public Builder toBuilder() {
        Builder b = system != null ? new Builder(system) : new Builder();
        b.router = router;
        return b.keyboard(keyboard)
                .mode(mode)
                .tone(tone)
                .abbrev(abbrev)
                .addFuzzies(fuzzies)
                .polyphone(polyphoneMode, polyphoneTable);
    }

    //region presets

    /**
     * Mandarin quanpin (full pinyin). For shuangpin schemes, use {@link
     * dev.vfyjxf.phonin.mandarin.MandarinOptions#shuangpin} in the {@code phonin-mandarin} module.
     */
    public static Options mandarinQuanpin() {
        return builder(PhoneticSystem.mandarin).build();
    }

    public static Options cantoneseQuanpin() {
        return builder(PhoneticSystem.cantonese).build();
    }

    public static Options zhuyin() {
        return builder(PhoneticSystem.zhuyin).build();
    }

    public static Options japaneseRomaji() {
        return builder(PhoneticSystem.japanese).build();
    }

    public static final class Builder {
        private final PhoneticSystem system; // null when building MULTI via the private constructor
        private CodepointRouter router;
        private Keyboard keyboard = Keyboard.identity;
        private MatchMode mode = MatchMode.CONTAINS;
        private TonePolicy tone = TonePolicy.IGNORE;
        private AbbrevPolicy abbrev = AbbrevPolicy.OFF;
        private final Set<FuzzyRule> fuzzies = new LinkedHashSet<>();
        private PolyphoneMode polyphoneMode = PolyphoneMode.OFF;
        private PolyphoneTable polyphoneTable = null;

        public Builder(PhoneticSystem system) {
            this.system = system;
        }

        private Builder() {
            this.system = null;
        }

        public Builder keyboard(Keyboard k) {
            this.keyboard = k == null ? Keyboard.identity : k;
            return this;
        }

        public Builder mode(MatchMode m) {
            this.mode = m;
            return this;
        }

        public Builder tone(TonePolicy t) {
            this.tone = t;
            return this;
        }

        public Builder abbrev(AbbrevPolicy a) {
            this.abbrev = a;
            return this;
        }

        public Builder addFuzzy(FuzzyRule r) {
            this.fuzzies.add(r);
            return this;
        }

        public Builder addFuzzies(Set<FuzzyRule> rs) {
            this.fuzzies.addAll(rs);
            return this;
        }

        /**
         * Activate context-aware polyphone disambiguation. {@code mode == PRECISE} requires a
         * single-system Options (no router) and a non-null {@code table}; {@code OFF} clears both
         * (any {@code table} argument is ignored).
         */
        public Builder polyphone(PolyphoneMode mode, PolyphoneTable table) {
            this.polyphoneMode = mode == null ? PolyphoneMode.OFF : mode;
            this.polyphoneTable = this.polyphoneMode == PolyphoneMode.PRECISE ? table : null;
            return this;
        }

        public Builder polyphoneMode(PolyphoneMode mode) {
            this.polyphoneMode = mode == null ? PolyphoneMode.OFF : mode;
            if (this.polyphoneMode != PolyphoneMode.PRECISE) this.polyphoneTable = null;
            return this;
        }

        public Builder polyphoneTable(PolyphoneTable table) {
            this.polyphoneTable = table;
            return this;
        }

        public Options build() {
            if ((system == null) == (router == null)) {
                // exactly one must be set: a single system OR a router, never both, never neither
                throw new IllegalStateException(
                        "Options requires exactly one of system / router to be set");
            }
            if (polyphoneMode == PolyphoneMode.PRECISE) {
                if (router != null)
                    throw new IllegalStateException(
                            "PRECISE polyphone mode requires a single system (no router)");
                if (polyphoneTable == null)
                    throw new IllegalStateException(
                            "PRECISE polyphone mode requires a polyphone table");
            }
            return new Options(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Options)) return false;
        Options other = (Options) o;
        return Objects.equals(system, other.system)
                && Objects.equals(router, other.router)
                && Objects.equals(keyboard, other.keyboard)
                && mode == other.mode
                && tone == other.tone
                && abbrev == other.abbrev
                && Objects.equals(fuzzies, other.fuzzies)
                && polyphoneMode == other.polyphoneMode
                && Objects.equals(polyphoneTable, other.polyphoneTable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                system,
                router,
                keyboard,
                mode,
                tone,
                abbrev,
                fuzzies,
                polyphoneMode,
                polyphoneTable);
    }
    //endregion
}
