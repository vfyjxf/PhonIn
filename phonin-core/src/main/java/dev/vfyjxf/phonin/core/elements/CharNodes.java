package dev.vfyjxf.phonin.core.elements;

import dev.vfyjxf.phonin.Keyboard;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.TonePolicy;
import dev.vfyjxf.phonin.fuzzy.FuzzyRule;
import dev.vfyjxf.phonin.model.CharEntry;
import dev.vfyjxf.phonin.model.Reading;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Build {@link CharNode}s from dataset entries / readings, applying the call's keyboard, tone
 * policy, fuzzy rules, and 简拼 setting. Extracted from {@link dev.vfyjxf.phonin.core.match.MatchContext}
 * so node construction is independent of per-call caches and polyphone segmentation state.
 */
public final class CharNodes {

    private CharNodes() {}

    /**
     * Build a node from all readings of a {@link CharEntry}, or a literal node if entry is null.
     */
    public static CharNode fromEntry(int codepoint, CharEntry entry, Options options) {
        if (entry == null) return CharNode.literal(codepoint);
        return fromReadings(codepoint, entry.readingsArray(), options);
    }

    /**
     * Build a node from a specific set of readings (e.g. one forced reading under PRECISE).
     */
    public static CharNode fromReadings(int codepoint, Reading[] readings, Options options) {
        Keyboard kb = options.keyboard();
        boolean sequence = options.sequence();
        List<Element> phonemes = new ArrayList<>(readings.length);
        for (Reading r : readings) {
            String[] strs = surfacesFor(r, kb, options);
            if (strs.length > 0) {
                phonemes.add(new Phoneme(strs, sequence));
            }
        }
        return new CharNode(codepoint, phonemes.toArray(new Element[0]));
    }

    /**
     * The matchable surface strings for one reading, under the given keyboard, tone policy, and
     * fuzzy rules. {@code fuzzy.py} defines every rule on the toneless syllable, so fuzzy variants
     * are derived from {@code toneConvention.strip(normalized)} (never the tone-bearing form) and
     * then each surface is mapped through the keyboard. Under {@link TonePolicy#STRICT} <em>with
     * the identity keyboard</em> the surface is the tone-bearing normalized form and fuzzy rules
     * are not applied (they are a toneless-surface concept); a non-identity keyboard is toneless by
     * construction, so STRICT is a no-op there.
     */
    private static String[] surfacesFor(Reading r, Keyboard kb, Options options) {
        if (kb.isIdentity() && options.tone() == TonePolicy.STRICT) {
            return new String[] {r.normalized};
        }
        // r.syllable is the precomputed toneless form; avoids a toneConvention.strip() on every
        // call for every reading.
        String syllable = r.syllable;
        Set<FuzzyRule> fuzzies = options.effectiveFuzzies();
        // Fast path: no fuzzies and no tone strictness means one surface per reading.
        if (fuzzies.isEmpty()) {
            if (kb.isIdentity()) {
                return syllable.isEmpty() ? new String[0] : new String[] {syllable};
            }
            String encoded = kb.encode(syllable);
            return (encoded == null || encoded.isEmpty()) ? new String[0] : new String[] {encoded};
        }
        LinkedHashSet<String> toneless = new LinkedHashSet<>();
        addIfNonEmpty(toneless, syllable);
        for (FuzzyRule rule : fuzzies) {
            for (String v : rule.variants(syllable)) {
                addIfNonEmpty(toneless, v);
            }
        }
        if (kb.isIdentity()) {
            return toneless.toArray(new String[0]);
        }
        // Non-identity keyboard: encode each toneless surface (canonical + fuzzy variants).
        LinkedHashSet<String> encoded = new LinkedHashSet<>();
        for (String s : toneless) {
            addIfNonEmpty(encoded, kb.encode(s));
        }
        return encoded.toArray(new String[0]);
    }

    private static void addIfNonEmpty(LinkedHashSet<String> set, String s) {
        if (s != null && !s.isEmpty()) set.add(s);
    }
}
