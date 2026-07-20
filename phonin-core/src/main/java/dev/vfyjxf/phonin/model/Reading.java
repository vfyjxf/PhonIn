package dev.vfyjxf.phonin.model;

import dev.vfyjxf.phonin.PhoneticSystem;

/**
 * One pronunciation of a character or word syllable. Carries the normalized (machine) form and the
 * toneless syllable derived from it by the owning {@link PhoneticSystem}'s {@link
 * dev.vfyjxf.phonin.ToneConvention}.
 */
public final class Reading {
    public final PhoneticSystem system;
    public final String normalized;
    public final String syllable; // toneless surface, derived from normalized once

    public Reading(PhoneticSystem system, String normalized, String syllable) {
        this.system = system;
        this.normalized = normalized;
        this.syllable = syllable;
    }

    public Reading(PhoneticSystem system, String normalized) {
        this(
                system,
                normalized,
                system == null ? normalized : system.toneConvention().strip(normalized));
    }

    @Override
    public String toString() {
        return normalized;
    }
}
