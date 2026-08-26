package dev.vfyjxf.phonin.model;

import dev.vfyjxf.phonin.PhoneticSystem;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * All {@link Reading}s registered for a single codepoint under a given system (polyphones carry
 * several). The engine tries every reading when matching; disambiguation is out of scope (word
 * entries already carry per-syllable readings for that). Mirrors the Python {@code CharEntry}.
 *
 * <p>Readings are stored as a bare {@code Reading[]} (copied from the loader's list at
 * construction) rather than an {@code ArrayList}, so a 1-reading char — the common case — does not
 * pay an {@code ArrayList} + {@code Object[]} wrapper. Across the ~146k-char dataset this removes
 * the per-char {@code ArrayList} objects (~3–4 MB).
 */
public final class CharEntry {
    public final int codepoint;
    private final Reading[] readings;

    public CharEntry(int codepoint, List<Reading> readings) {
        this.codepoint = codepoint;
        this.readings = readings.toArray(new Reading[0]);
    }

    /**
     * An unmodifiable view of this char's readings (backed by the array; no copy).
     */
    public List<Reading> readings() {
        return Collections.unmodifiableList(Arrays.asList(readings));
    }

    /**
     * The raw readings array; for internal engine use where {@link List} overhead matters.
     */
    public Reading[] readingsArray() {
        return readings;
    }

    public PhoneticSystem system() {
        return readings.length == 0 ? null : readings[0].system;
    }
}
