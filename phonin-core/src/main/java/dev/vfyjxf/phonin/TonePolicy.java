package dev.vfyjxf.phonin;

/**
 * How tone information in a reading is treated during matching.
 */
public enum TonePolicy {
    /**
     * Tones are ignored: the matchable surface is the toneless syllable. The default.
     */
    IGNORE,
    /**
     * Tones are required: the surface is the tone-marked normalized form.
     */
    STRICT
}
