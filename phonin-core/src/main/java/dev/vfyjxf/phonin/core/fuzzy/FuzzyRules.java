package dev.vfyjxf.phonin.core.fuzzy;

import dev.vfyjxf.phonin.fuzzy.FuzzyRule;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The catalogue of built-in fuzzy-phonetic rules, mirroring {@code tools/phonin_data/fuzzy.py}
 * <b>exactly</b> — same names, same string transforms, same SWAP/MERGE semantics — so the fuzzy
 * cases generated in Python are a faithful regression net for the engine. The {@link
 * FuzzyRulesParityTest} enforces byte-for-byte agreement with {@code fuzzy.py} across every real
 * surface.
 *
 * <p>Two rule shapes (see {@code fuzzy.py}):
 *
 * <ul>
 *   <li><b>SWAP</b> — a symmetric confusion (zh&lt;-&gt;z, an&lt;-&gt;ang, n&lt;-&gt;l,
 *       u&lt;-&gt;v, Hepburn&lt;-&gt;Kunrei). {@link FuzzyRule#variants} returns the counterpart.
 *   <li><b>MERGE</b> — an asymmetric, user-side simplification against a richer canonical form
 *       (drop initial ng, drop w after g/k, collapse a doubled vowel, ae-&gt;e). {@link
 *       FuzzyRule#variants} returns only the reduced form.
 * </ul>
 *
 * <p>All rules operate on the toneless surface the {@link dev.vfyjxf.phonin.core.elements.Phoneme}
 * carries.
 *
 * <p>Per the codebase convention, the rule constants are {@code static final} fields in
 * lowerCamelCase; their {@link FuzzyRule#name()} strings are the UPPER_SNAKE identifiers shared
 * with the dataset's {@code fuzzies} field.
 */
public final class FuzzyRules {

    private FuzzyRules() {}

    //region Mandarin
    public static final FuzzyRule fuzzyZhZ = initialSwap("FUZZY_ZH_Z", "zh", "z");
    public static final FuzzyRule fuzzyChC = initialSwap("FUZZY_CH_C", "ch", "c");
    public static final FuzzyRule fuzzyShS = initialSwap("FUZZY_SH_S", "sh", "s");
    public static final FuzzyRule fuzzyAngAn = finalSwap("FUZZY_ANG_AN", "ang", "an");
    public static final FuzzyRule fuzzyEngEn = finalSwap("FUZZY_ENG_EN", "eng", "en");
    public static final FuzzyRule fuzzyIngIn = finalSwap("FUZZY_ING_IN", "ing", "in");
    public static final FuzzyRule fuzzyUV = rule("FUZZY_U_V", FuzzyRules::uV);

    //endregion
    //region Cantonese
    public static final FuzzyRule fuzzyNL = rule("FUZZY_N_L", FuzzyRules::nL);
    public static final FuzzyRule fuzzyNgOmit = rule("FUZZY_NG_OMIT", FuzzyRules::ngOmit);
    public static final FuzzyRule fuzzyGwG = rule("FUZZY_GW_G", FuzzyRules::gwG);

    //endregion
    //region Zhuyin (Bopomofo retroflex <-> dental)
    public static final FuzzyRule fuzzyZYZhZ = initialSwap("FUZZY_ZY_ZH_Z", "ㄓ", "ㄗ");
    public static final FuzzyRule fuzzyZYChC = initialSwap("FUZZY_ZY_CH_C", "ㄔ", "ㄘ");
    public static final FuzzyRule fuzzyZYShS = initialSwap("FUZZY_ZY_SH_S", "ㄕ", "ㄙ");

    //endregion
    //region Japanese
    public static final FuzzyRule fuzzyHepburnKunrei =
            rule("FUZZY_HEPBURN_KUNREI", FuzzyRules::hepburnKunrei);
    public static final FuzzyRule fuzzyLongVowel = rule("FUZZY_LONG_VOWEL", FuzzyRules::longVowel);

    //endregion
    //region Korean
    // Korean now uses 2-bulsik keyboard input (QWERTY key sequences) instead of Revised
    // Romanization. The former romaji-based fuzzy rules (ae↔e, r↔l, oe↔we) are removed because
    // they operated on romaji substrings that do not appear in keyboard key sequences.
    // Keyboard-specific fuzzy rules (adjacent-key errors, shift-key errors) may be added later.

    //endregion
    //region registry

    private static final List<FuzzyRule> RULES =
            Arrays.asList(
                    fuzzyZhZ,
                    fuzzyChC,
                    fuzzyShS,
                    fuzzyAngAn,
                    fuzzyEngEn,
                    fuzzyIngIn,
                    fuzzyUV,
                    fuzzyNL,
                    fuzzyNgOmit,
                    fuzzyGwG,
                    fuzzyZYZhZ,
                    fuzzyZYChC,
                    fuzzyZYShS,
                    fuzzyHepburnKunrei,
                    fuzzyLongVowel);

    private static final Map<String, FuzzyRule> BY_NAME;

    static {
        Map<String, FuzzyRule> m = new HashMap<>();
        for (FuzzyRule r : RULES) {
            m.put(r.name(), r);
        }
        BY_NAME = Collections.unmodifiableMap(m);
    }

    /**
     * All fifteen rules, in catalogue order.
     */
    public static List<FuzzyRule> rules() {
        return RULES;
    }

    /**
     * Look up a rule by its dataset identifier (e.g. {@code "FUZZY_ZH_Z"}), or {@code null} if no
     * such rule exists.
     */
    public static FuzzyRule byName(String name) {
        return BY_NAME.get(name);
    }

    //endregion
    //region rule shapes (mirror fuzzy.py)

    /**
     * Symmetric swap of a leading initial {@code a} &lt;-&gt; {@code b}. {@code a} may be longer
     * than {@code b} (zh vs z); it is matched first so zh is not shadowed by z.
     */
    private static FuzzyRule initialSwap(String name, String a, String b) {
        return new Named(name) {
            @Override
            public Set<String> variants(String s) {
                if (s.startsWith(a)) return set(b + s.substring(a.length()));
                if (s.startsWith(b)) return set(a + s.substring(b.length()));
                return Collections.emptySet();
            }
        };
    }

    /**
     * Symmetric swap of a trailing final {@code a} &lt;-&gt; {@code b} (e.g. ang &lt;-&gt; an).
     */
    private static FuzzyRule finalSwap(String name, String a, String b) {
        return new Named(name) {
            @Override
            public Set<String> variants(String s) {
                if (s.endsWith(a)) return set(s.substring(0, s.length() - a.length()) + b);
                if (s.endsWith(b)) return set(s.substring(0, s.length() - b.length()) + a);
                return Collections.emptySet();
            }
        };
    }

    /**
     * Initials after which the u/ü distinction is real (n, l carry ü as 'v'; j, q, x, y as 'u').
     */
    private static final Set<Character> UV_INITIALS =
            new LinkedHashSet<>(Arrays.asList('n', 'l', 'j', 'q', 'x', 'y'));

    /**
     * Symmetric u &lt;-&gt; v (ü) swap, but only after a ü-capable initial.
     */
    private static Set<String> uV(String s) {
        if (s.isEmpty() || !UV_INITIALS.contains(s.charAt(0))) return Collections.emptySet();
        char initial = s.charAt(0);
        String rest = s.substring(1);
        if (rest.contains("u")) return set(initial + rest.replace("u", "v"));
        if (rest.contains("v")) return set(initial + rest.replace("v", "u"));
        return Collections.emptySet();
    }

    /**
     * Cantonese initial n &lt;-&gt; l, excluding ng-initial (which has its own rule).
     */
    private static Set<String> nL(String s) {
        if (s.startsWith("ng")) return Collections.emptySet();
        if (s.startsWith("n")) return set("l" + s.substring(1));
        if (s.startsWith("l")) return set("n" + s.substring(1));
        return Collections.emptySet();
    }

    /**
     * Cantonese: drop an initial ng (我 ngo -&gt; o). User-side merge.
     */
    private static Set<String> ngOmit(String s) {
        if (s.startsWith("ng") && s.length() > 2) return set(s.substring(2));
        return Collections.emptySet();
    }

    /**
     * Cantonese: drop the w after a labialized velar initial (gw -&gt; g, kw -&gt; k). Merge.
     */
    private static Set<String> gwG(String s) {
        if (s.startsWith("gw") && s.length() > 2) return set("g" + s.substring(2));
        if (s.startsWith("kw") && s.length() > 2) return set("k" + s.substring(2));
        return Collections.emptySet();
    }

    // Hepburn <-> Kunrei-shiki romaji equivalences (longest clusters first to avoid shadowing).
    private static final String[][] JP_PAIRS = {
        {"shi", "si"},
        {"tsu", "tu"},
        {"sha", "sya"},
        {"shu", "syu"},
        {"sho", "syo"},
        {"cha", "tya"},
        {"chu", "tyu"},
        {"cho", "tyo"},
        {"ja", "zya"},
        {"ju", "zyu"},
        {"jo", "zyo"},
        {"chi", "ti"},
        {"ji", "zi"},
        {"fu", "hu"},
    };

    // A cluster starts at the beginning of the surface or after a vowel / 'n'. Match a Hepburn or
    // Kunrei cluster only there, so the "hu" inside "chuu" (c-h-u-u) is NOT the standalone ふ. A
    // negated fixed-width lookbehind: not preceded by any consonant other than 'n'.
    private static final String CLUSTER_START = "(?<![bcdfghjklmpqrstvwxyz])";

    private static final class JpPair {
        final Pattern hepburn; // hepburn cluster (e.g. "shi")
        final String kunrei; // kunrei replacement (e.g. "si")
        final Pattern kunreiP; // kunrei cluster (e.g. "si")
        final String hepburnR; // hepburn replacement (e.g. "shi")

        JpPair(String h, String k) {
            this.hepburn = Pattern.compile(CLUSTER_START + Pattern.quote(h));
            this.kunrei = k;
            this.kunreiP = Pattern.compile(CLUSTER_START + Pattern.quote(k));
            this.hepburnR = h;
        }
    }

    private static final List<JpPair> JP_COMPILED;

    static {
        JpPair[] ps = new JpPair[JP_PAIRS.length];
        for (int i = 0; i < JP_PAIRS.length; i++) {
            ps[i] = new JpPair(JP_PAIRS[i][0], JP_PAIRS[i][1]);
        }
        JP_COMPILED = Collections.unmodifiableList(Arrays.asList(ps));
    }

    /**
     * Symmetric Hepburn &lt;-&gt; Kunrei romaji confusion (cluster-boundary aware).
     */
    private static Set<String> hepburnKunrei(String s) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JpPair p : JP_COMPILED) {
            if (p.hepburn.matcher(s).find()) {
                out.add(p.hepburn.matcher(s).replaceAll(Matcher.quoteReplacement(p.kunrei)));
            }
            if (p.kunreiP.matcher(s).find()) {
                out.add(p.kunreiP.matcher(s).replaceAll(Matcher.quoteReplacement(p.hepburnR)));
            }
        }
        return out.isEmpty() ? Collections.<String>emptySet() : Collections.unmodifiableSet(out);
    }

    /**
     * Collapse a long/doubled vowel (chuu -&gt; chu, too -&gt; to, tou -&gt; to). User-side merge.
     */
    private static Set<String> longVowel(String s) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (s.contains("uu")) out.add(s.replace("uu", "u"));
        if (s.contains("oo")) out.add(s.replace("oo", "o"));
        if (s.contains("ou")) out.add(s.replace("ou", "o"));
        return out.isEmpty() ? Collections.<String>emptySet() : Collections.unmodifiableSet(out);
    }

    //endregion
    //region helpers

    /**
     * Builds an unmodifiable, de-duplicated set of the non-null, non-empty inputs.
     */
    private static Set<String> set(String... xs) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        for (String x : xs) {
            if (x != null && !x.isEmpty()) s.add(x);
        }
        return s.isEmpty() ? Collections.<String>emptySet() : Collections.unmodifiableSet(s);
    }

    private static FuzzyRule rule(String name, final VariantsFn fn) {
        return new Named(name) {
            @Override
            public Set<String> variants(String surface) {
                return fn.apply(surface);
            }
        };
    }

    /**
     * A function returning the variant set for a surface (used for the non-parameterised rules).
     */
    private interface VariantsFn {
        Set<String> apply(String surface);
    }

    private abstract static class Named implements FuzzyRule {
        private final String name;

        Named(String name) {
            this.name = name;
        }

        @Override
        public final String name() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
    //endregion
}
