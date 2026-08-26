package dev.vfyjxf.phonin;

import dev.vfyjxf.phonin.fuzzy.FuzzyRule;
import dev.vfyjxf.phonin.model.CharEntry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * A phonetic spelling system <em>is</em> this object: its identity, its matching behaviour, its
 * tone convention, and the dataset (character readings) it matches against — all passed around
 * directly. There is no separate key type and no registry; behaviour is not looked up by name, it
 * lives on the instance. Construct one with {@code new PhoneticSystem(...)} to define your own
 * (Hakka, Vietnamese, ...); the predefined systems are available as constants.
 *
 * <p>Two instances are equal when their {@link #name} is equal, so a {@link
 * dev.vfyjxf.phonin.model.Reading}'s system can be compared against {@link Options#system()} by
 * value without a lookup table.
 *
 * <p>Every shipped system stores its matchable surface precomputed in the data pipeline (the
 * toneless core of romanizations and Bopomofo), so no per-system code is needed in the matcher.
 */
public class PhoneticSystem {

    public final String name;

    private final boolean abbreviable;
    private final Set<FuzzyRule> defaultFuzzies;
    private final ToneConvention toneConvention;

    // A primitive int->CharEntry map: the codepoint key is stored unboxed (vs HashMap<Integer,
    // ...>, which paid a 16-byte Integer + 32-byte Node per char). Saves ~5-6 MB across the
    // ~146k-char dataset. Swapped atomically on (re)load so matchers never see a half-built table.
    private volatile Int2ObjectMap<CharEntry> chars = new Int2ObjectOpenHashMap<>();

    public PhoneticSystem(
            String name,
            boolean abbreviable,
            Set<FuzzyRule> defaultFuzzies,
            ToneConvention toneConvention) {
        this.name = Objects.requireNonNull(name, "system name");
        this.abbreviable = abbreviable;
        this.defaultFuzzies =
                defaultFuzzies == null
                        ? Collections.<FuzzyRule>emptySet()
                        : Collections.unmodifiableSet(defaultFuzzies);
        this.toneConvention = toneConvention == null ? ToneConvention.NONE : toneConvention;
    }

    /**
     * A non-abbreviable system with no default fuzzies and no tone marking.
     */
    public PhoneticSystem(String name) {
        this(name, false, Collections.<FuzzyRule>emptySet(), ToneConvention.NONE);
    }

    //region behaviour

    public boolean abbreviable() {
        return abbreviable;
    }

    public Set<FuzzyRule> defaultFuzzies() {
        return defaultFuzzies;
    }

    public ToneConvention toneConvention() {
        return toneConvention;
    }

    //endregion
    //region dataset (populated by the loader; readable by the matcher)

    public CharEntry charEntry(int codepoint) {
        return chars.get(codepoint);
    }

    public int charCount() {
        return chars.size();
    }

    /**
     * Register a character's readings for this system (programmatic dataset building).
     */
    public void putChar(CharEntry entry) {
        chars.put(entry.codepoint, entry);
    }

    /**
     * Drop all loaded data (atomic: matchers keep seeing the old table until this returns).
     */
    public void clearData() {
        chars = new Int2ObjectOpenHashMap<>();
    }

    /**
     * Loader entry point: atomically replace the char table, so a mid-load state is never visible
     * to concurrent matchers.
     */
    public void replaceData(Int2ObjectMap<CharEntry> newChars) {
        chars = Objects.requireNonNull(newChars, "newChars");
    }

    //endregion
    //region predefined systems

    public static final PhoneticSystem mandarin =
            new PhoneticSystem("MANDARIN", true, Collections.emptySet(), ToneConvention.DIGIT);
    public static final PhoneticSystem cantonese =
            new PhoneticSystem("CANTONESE", false, Collections.emptySet(), ToneConvention.DIGIT);
    public static final PhoneticSystem zhuyin =
            new PhoneticSystem("ZHUYIN", false, Collections.emptySet(), ToneConvention.ZHUYIN);
    public static final PhoneticSystem japanese =
            new PhoneticSystem("JAPANESE", false, Collections.emptySet(), ToneConvention.NONE);
    public static final PhoneticSystem korean =
            new PhoneticSystem("KOREAN", false, Collections.emptySet(), ToneConvention.NONE);

    @Override
    public boolean equals(Object o) {
        return (this == o)
                || (o instanceof PhoneticSystem && name.equals(((PhoneticSystem) o).name));
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
    //endregion
}
