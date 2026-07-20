package dev.vfyjxf.phonin.fuzzy;

import java.util.Set;

/**
 * A named phonetic-equivalence rule (e.g. Mandarin zh&lt;-&gt;z). The engine applies a rule to a
 * phoneme's surface string to expose {@link #variants(String)} alongside the original surface. The
 * Java implementations in {@code FuzzyRules} mirror {@code tools/phonin_data/fuzzy.py}
 * <b>exactly</b> (same name, same transform) so the fuzzy cases generated in Python are a faithful
 * regression net for the engine.
 *
 * <p>Phase 2B ships with no rules enabled; concrete rules land in Phase 2F.
 */
public interface FuzzyRule {
    /**
     * Stable identifier shared with the dataset's {@code fuzzies} field, e.g. {@code "FUZZY_ZH_Z"}.
     */
    String name();

    /**
     * Alternative surface strings (NOT including the original). Empty if the rule does not fire on
     * this surface.
     */
    Set<String> variants(String surface);
}
