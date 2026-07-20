package io.phonin.fuzzy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Asserts every {@link FuzzyRules} rule reproduces {@code tools/phonin_data/fuzzy.py} exactly, on
 * every real surface the engine matches. The golden tables ({@code phonin/fuzzy/golden-<rule>.tsv})
 * are emitted by {@code tools/emit_fuzzy_golden.py}, which runs {@code fuzzy.py} as an independent
 * oracle over the surfaces drawn from each system's generated FULL char cases.
 *
 * <p>This is the strong net for the trickiest port ({@link FuzzyRules#fuzzyHepburnKunrei}'s
 * cluster-boundary regex): the positive fuzzy cases only prove a source char matches its variant
 * (the missing-variant direction). This test also catches the extra-variant direction (Java
 * producing a variant Python does not) and surfaces Python leaves untouched.
 */
class FuzzyRulesParityTest {

    private static final String[] RULES = {
        "FUZZY_ZH_Z",
        "FUZZY_CH_C",
        "FUZZY_SH_S",
        "FUZZY_ANG_AN",
        "FUZZY_ENG_EN",
        "FUZZY_ING_IN",
        "FUZZY_U_V",
        "FUZZY_N_L",
        "FUZZY_NG_OMIT",
        "FUZZY_GW_G",
        "FUZZY_ZY_ZH_Z",
        "FUZZY_ZY_CH_C",
        "FUZZY_ZY_SH_S",
        "FUZZY_HEPBURN_KUNREI",
        "FUZZY_LONG_VOWEL",
    };

    @Test
    void javaMirrorsPythonGoldenOnEverySurface() {
        for (String rule : RULES) {
            FuzzyRule impl = FuzzyRules.byName(rule);
            assertThat(impl).as("rule registered: %s", rule).isNotNull();
            int checked = 0;
            for (String line : readLines("phonin/fuzzy/golden-" + rule.toLowerCase() + ".tsv")) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int tab = line.indexOf('\t');
                String surface = tab < 0 ? line : line.substring(0, tab);
                String rest = tab < 0 ? "" : line.substring(tab + 1);
                Set<String> expected = parseCsv(rest);
                Set<String> got = impl.variants(surface);
                assertThat(got).as("%s variants(%s)", rule, surface).isEqualTo(expected);
                checked++;
            }
            assertThat(checked).as("%s golden surfaces checked", rule).isGreaterThan(0);
        }
    }

    private static Set<String> parseCsv(String csv) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        if (!csv.isEmpty()) {
            for (String v : csv.split(",", -1)) {
                if (!v.isEmpty()) s.add(v);
            }
        }
        return s;
    }

    private static List<String> readLines(String resource) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = FuzzyRulesParityTest.class.getClassLoader();
        List<String> lines = new ArrayList<>();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("golden table not found on classpath: " + resource);
            }
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) lines.add(line);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + resource, e);
        }
        return lines;
    }
}
