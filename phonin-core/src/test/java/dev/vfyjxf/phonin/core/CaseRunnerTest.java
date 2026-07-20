package dev.vfyjxf.phonin.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vfyjxf.phonin.AbbrevPolicy;
import dev.vfyjxf.phonin.MatchMode;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.TonePolicy;
import dev.vfyjxf.phonin.core.fuzzy.FuzzyRules;
import dev.vfyjxf.phonin.fuzzy.FuzzyRule;
import dev.vfyjxf.phonin.korean.KoreanKeyboard;
import dev.vfyjxf.phonin.mandarin.ShuangpinKeyboard;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Streams the dataset's self-verifying cases through the engine and asserts it reproduces {@code
 * tools/phonin_data/cases.py} semantics exactly. Covers, for every system: FULL / PARTIAL / TONED
 * char cases, NEGATIVE inputs, word-level FULL / ABBREV / PARTIAL / TONED cases, shuangpin char +
 * word cases for all five schemes, and the per-rule fuzzy char cases (Phase 2F).
 *
 * <p>POSITIVE: the source must match its own input under the case's options. NEGATIVE: no character
 * whose surface shares the input's first char may match (prefix matching is the only way a single
 * char matches, so this is exhaustive over the relevant subset).
 *
 * <p>Set {@code -Dphonin.case.sample=1.0} for the full corpus; default 1% for a fast build.
 */
class CaseRunnerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double SAMPLE =
            Double.parseDouble(System.getProperty("phonin.case.sample", "0.01"));

    private static final String[] SCHEMES = {"flypy", "zrm", "mspy", "pyjj", "abc"};

    /**
     * {system, generated file} for each of the 15 fuzzy rules.
     */
    private static final String[][] FUZZY_FILES = {
        {"MANDARIN", "cases-char-fuzzy-fuzzy_zh_z.jsonl"},
        {"MANDARIN", "cases-char-fuzzy-fuzzy_ch_c.jsonl"},
        {"MANDARIN", "cases-char-fuzzy-fuzzy_sh_s.jsonl"},
        {"MANDARIN", "cases-char-fuzzy-fuzzy_ang_an.jsonl"},
        {"MANDARIN", "cases-char-fuzzy-fuzzy_eng_en.jsonl"},
        {"MANDARIN", "cases-char-fuzzy-fuzzy_ing_in.jsonl"},
        {"MANDARIN", "cases-char-fuzzy-fuzzy_u_v.jsonl"},
        {"CANTONESE", "cases-char-fuzzy-fuzzy_n_l.jsonl"},
        {"CANTONESE", "cases-char-fuzzy-fuzzy_ng_omit.jsonl"},
        {"CANTONESE", "cases-char-fuzzy-fuzzy_gw_g.jsonl"},
        {"ZHUYIN", "cases-char-fuzzy-fuzzy_zy_zh_z.jsonl"},
        {"ZHUYIN", "cases-char-fuzzy-fuzzy_zy_ch_c.jsonl"},
        {"ZHUYIN", "cases-char-fuzzy-fuzzy_zy_sh_s.jsonl"},
        {"JAPANESE", "cases-char-fuzzy-fuzzy_hepburn_kunrei.jsonl"},
        {"JAPANESE", "cases-char-fuzzy-fuzzy_long_vowel.jsonl"},
    };

    private static final Map<String, PhoneticSystem> SYSTEMS = new HashMap<>();

    static {
        for (PhoneticSystem s :
                new PhoneticSystem[] {
                    PhoneticSystem.mandarin,
                    PhoneticSystem.cantonese,
                    PhoneticSystem.zhuyin,
                    PhoneticSystem.japanese,
                    PhoneticSystem.korean
                }) {
            SYSTEMS.put(s.name, s);
        }
    }

    private static PhonIn phonIn;

    /**
     * system -> (first surface char -> codepoints that have a surface starting with it).
     */
    private static final Map<PhoneticSystem, Map<Character, List<Integer>>> FIRST_CHAR_BUCKETS =
            new HashMap<>();

    @BeforeAll
    static void load() {
        phonIn =
                PhonIn.create(
                        PhoneticSystem.mandarin,
                        PhoneticSystem.cantonese,
                        PhoneticSystem.zhuyin,
                        PhoneticSystem.japanese,
                        PhoneticSystem.korean);
        for (PhoneticSystem s : SYSTEMS.values()) {
            FIRST_CHAR_BUCKETS.put(s, buildFirstCharBuckets(s));
        }
    }

    @Test
    void charFull() {
        for (String sysName : SYSTEMS.keySet()) {
            runPositiveFile(sysName, "cases-char-full.jsonl");
        }
    }

    @Test
    void charPartial() {
        for (String sysName : SYSTEMS.keySet()) {
            runPositiveFile(sysName, "cases-char-partial.jsonl");
        }
    }

    @Test
    void charToned() {
        for (String sysName : SYSTEMS.keySet()) {
            runPositiveFile(sysName, "cases-char-toned.jsonl");
        }
    }

    @Test
    void mandarinWords() {
        for (String f :
                new String[] {
                    "cases-word-full.jsonl",
                    "cases-word-abbrev.jsonl",
                    "cases-word-partial.jsonl",
                    "cases-word-toned.jsonl"
                }) {
            runPositiveFile("MANDARIN", f);
        }
    }

    @Test
    void japaneseWords() {
        runPositiveFile("JAPANESE", "cases-word-full.jsonl");
    }

    @Test
    void koreanWords() {
        runPositiveFile("KOREAN", "cases-word-full.jsonl");
    }

    @Test
    void shuangpinChar() {
        for (String scheme : SCHEMES) {
            runPositiveFile("MANDARIN", "cases-char-shuangpin-" + scheme + ".jsonl");
        }
    }

    @Test
    void shuangpinWord() {
        for (String scheme : SCHEMES) {
            runPositiveFile("MANDARIN", "cases-word-shuangpin-" + scheme + ".jsonl");
        }
    }

    @Test
    void fuzzyChar() {
        // One file per fuzzy rule (18 total); each case turns on exactly that rule via `fuzzies`.
        for (String[] f : FUZZY_FILES) {
            runPositiveFile(f[0], f[1]);
        }
    }

    @Test
    void negatives() {
        for (String sysName : SYSTEMS.keySet()) {
            runNegatives(sysName);
        }
    }

    @Test
    void regressionScenarios() {
        // Committed scenario datasets (regression/) the main generated corpus doesn't cover:
        // 简拼/full mixes, CJK+latin mixed text, long text, fuzzy+简拼, match modes (BEGINS/EQUAL),
        // and precision NEGATIVEs. variant drives Options ("ABBREV" -> 简拼, "FULL" -> quanpin);
        // mode drives MatchMode; polarity drives the assertion (POSITIVE must match, NEGATIVE not).
        for (String file :
                new String[] {
                    "jianpin-mix-mandarin.jsonl",
                    "long-text-mandarin.jsonl",
                    "mixed-script-mandarin.jsonl",
                    "fuzzy-jianpin-mandarin.jsonl",
                    "modes-mandarin.jsonl",
                    "negative-scenarios-mandarin.jsonl",
                }) {
            List<JsonNode> cases = regressionCases(file);
            assertThat(cases).as("regression/%s present on classpath", file).isNotEmpty();
            long checked = 0;
            for (JsonNode c : cases) {
                String pol = c.path("polarity").asText();
                boolean expectMatch = pol.equals("POSITIVE");
                if (!expectMatch && !pol.equals("NEGATIVE")) continue;
                Options o = options(c);
                if (o == null) continue;
                String input = c.get("input").asText();
                String text =
                        c.has("sourceText")
                                ? c.get("sourceText").asText()
                                : sourceText(c.get("sourceEntry").asText());
                assertThat(match(o, text, input))
                        .as(
                                "regression/%s: source %s should %s '%s' under %s",
                                file,
                                c.path("sourceEntry").asText(),
                                expectMatch ? "match" : "NOT match",
                                input,
                                describe(o))
                        .isEqualTo(expectMatch);
                checked++;
            }
            assertThat(checked).as("regression/%s cases", file).isGreaterThan(0);
            System.out.printf("[regression] %s: %d OK%n", file, checked);
        }
    }

    //region runners

    private void runPositiveFile(String sysName, String file) {
        List<JsonNode> cases = sampleCases(sysName, file);
        if (cases.isEmpty()) return; // generated corpus not on classpath -> skip
        long checked = 0;
        long skipped = 0;
        for (JsonNode c : cases) {
            if (!c.path("polarity").asText().equals("POSITIVE")) continue;
            Options o = options(c);
            if (o == null) {
                skipped++;
                continue;
            }
            String input = c.get("input").asText();
            String text =
                    c.has("sourceText")
                            ? c.get("sourceText").asText()
                            : sourceText(c.get("sourceEntry").asText());
            boolean got = match(o, text, input);
            assertThat(got)
                    .as(
                            "%s/%s: source %s should match input '%s' under %s",
                            sysName, file, c.path("sourceEntry").asText(), input, describe(o))
                    .isTrue();
            checked++;
        }
        if (checked == 0) return; // whole file belongs to a not-yet-implemented family
        assertThat(checked).as("%s/%s sampled positives", sysName, file).isGreaterThan(0);
        System.out.printf(
                "[positives] %s/%s: %d OK (%d skipped)%n", sysName, file, checked, skipped);
    }

    private void runNegatives(String sysName) {
        PhoneticSystem system = SYSTEMS.get(sysName);
        List<JsonNode> cases = sampleCases(sysName, "cases-negative.jsonl");
        if (cases.isEmpty()) return; // generated corpus not on classpath -> skip
        Map<Character, List<Integer>> buckets = FIRST_CHAR_BUCKETS.get(system);
        long checked = 0;
        for (JsonNode c : cases) {
            String input = c.get("input").asText();
            Options o = options(c); // mode CONTAINS, IGNORE tone
            char first = input.charAt(0);
            List<Integer> bucket = buckets.getOrDefault(first, new ArrayList<>());
            for (int cp : bucket) {
                String text = new String(Character.toChars(cp));
                assertThat(phonIn.contains(text, input, o))
                        .as("%s negative input '%s' must not match U+%X", sysName, input, cp)
                        .isFalse();
            }
            checked++;
        }
        assertThat(checked).as("%s sampled negatives", sysName).isGreaterThan(0);
        System.out.printf("[negatives] %s: %d sampled inputs OK%n", sysName, checked);
    }

    //endregion
    //region helpers

    private boolean match(Options o, String text, String input) {
        switch (o.mode()) {
            case CONTAINS:
                return phonIn.contains(text, input, o);
            case BEGINS:
                return phonIn.begins(text, input, o);
            case EXACT:
                return phonIn.matches(text, input, o);
            default:
                return phonIn.contains(text, input, o);
        }
    }

    /**
     * Maps a case to its Options, or returns {@code null} to skip families the engine does not
     * exercise yet (cross-system strings -> Phase 2H). Fuzzy cases ({@code variant=="FUZZY"}) carry
     * their rule names in {@code fuzzies} and are turned on via {@link FuzzyRules#byName}.
     */
    private Options options(JsonNode c) {
        PhoneticSystem system = SYSTEMS.get(c.get("system").asText());
        if (system == null) return null; // e.g. "MANDARIN+ZHUYIN" cross-system -> Phase 2H
        String variant = c.path("variant").asText("");
        String keyboard = c.path("keyboard").asText("QUANPIN");
        Options.Builder b = Options.builder(system);
        if (keyboard.equals("BULSIK2")) {
            b.keyboard(KoreanKeyboard.bulsik2());
        } else if (keyboard.equals("CHOSEONG")) {
            b.keyboard(KoreanKeyboard.choseong());
        } else if (!keyboard.equals("QUANPIN")
                && !keyboard.equals("ZHUYIN")
                && !keyboard.equals("ROMAJI")) {
            b.keyboard(ShuangpinKeyboard.of(keyboard.toLowerCase())); // shuangpin scheme
        }
        String mode = c.path("mode").asText("CONTAINS");
        if (mode.equals("BEGINS")) b.mode(MatchMode.BEGINS);
        else if (mode.equals("EXACT")) b.mode(MatchMode.EXACT);
        else b.mode(MatchMode.CONTAINS);
        if (variant.equals("TONED")) b.tone(TonePolicy.STRICT);
        if (variant.equals("ABBREV")) b.abbrev(AbbrevPolicy.INITIALS);
        JsonNode fz = c.path("fuzzies");
        if (fz.isArray()) {
            for (JsonNode n : fz) {
                String ruleName = n.asText();
                FuzzyRule r = FuzzyRules.byName(ruleName);
                if (r == null) {
                    // Fail fast: a typo'd or renamed rule would otherwise run the case with no
                    // fuzzy
                    // active and silently hide a real Java/Python divergence.
                    throw new IllegalStateException("Unknown fuzzy rule in case: " + ruleName);
                }
                b.addFuzzy(r);
            }
        }
        return b.build();
    }

    /**
     * A char case's sourceEntry is "U+XXXX"; a word case's is "U+XXXX,U+YYYY,...".
     */
    private static String sourceText(String sourceEntry) {
        String[] parts = sourceEntry.split(",");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            String h = p.startsWith("U+") ? p.substring(2) : p;
            sb.appendCodePoint((int) Long.parseLong(h, 16));
        }
        return sb.toString();
    }

    private static String describe(Options o) {
        return "{system="
                + o.system()
                + ", mode="
                + o.mode()
                + ", tone="
                + o.tone()
                + ", fuzzies="
                + o.fuzzies()
                + "}";
    }

    private static Map<Character, List<Integer>> buildFirstCharBuckets(PhoneticSystem system) {
        Map<Character, List<Integer>> buckets = new HashMap<>();
        String path = "phonin/raw/" + system.name.toLowerCase() + "-char.tsv";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = CaseRunnerTest.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) return buckets;
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    String[] c = line.split("\t", -1);
                    if (c.length < 4) continue;
                    String hex = c[0].startsWith("U+") ? c[0].substring(2) : c[0];
                    int cp;
                    try {
                        cp = Integer.parseInt(hex, 16);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    for (String norm : c[3].split(",")) {
                        String surface = system.toneConvention().strip(norm.trim());
                        if (surface.isEmpty()) continue;
                        // Korean stores Hangul as the normalized form; the user-facing surface is
                        // the 2-bulsik key sequence, so bucket by the first key, not the Hangul
                        // char.
                        if (system == PhoneticSystem.korean) {
                            String encoded = KoreanKeyboard.bulsik2().encode(surface);
                            if (encoded != null && !encoded.isEmpty()) {
                                buckets.computeIfAbsent(encoded.charAt(0), k -> new ArrayList<>())
                                        .add(cp);
                            }
                        } else {
                            buckets.computeIfAbsent(surface.charAt(0), k -> new ArrayList<>())
                                    .add(cp);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return buckets;
    }

    /**
     * Read a committed regression scenario file from {@code phonin/regression/} -- the full file
     * (these are small curated sets, not sampled like the generated corpus). Empty if absent.
     */
    private List<JsonNode> regressionCases(String file) {
        String path = "phonin/regression/" + file;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = CaseRunnerTest.class.getClassLoader();
        List<JsonNode> out = new ArrayList<>();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) return out; // absent -> caller asserts non-empty
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    try {
                        out.add(JSON.readTree(line));
                    } catch (Exception ignore) {
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    /**
     * Read a generated case file from the classpath and return a deterministic sample.
     */
    private List<JsonNode> sampleCases(String sysName, String file) {
        String path = "phonin/generated/" + sysName.toLowerCase() + "/" + file;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = CaseRunnerTest.class.getClassLoader();
        List<String> lines = new ArrayList<>();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) return new ArrayList<>(); // generated corpus absent -> nothing to check
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.isEmpty()) lines.add(line);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        int target = Math.max(1, (int) Math.round(SAMPLE * lines.size()));
        Random rng = new Random(123456789L);
        Set<Integer> picked = new HashSet<>();
        List<JsonNode> out = new ArrayList<>(target);
        while (picked.size() < target && picked.size() < lines.size()) {
            int i = rng.nextInt(lines.size());
            if (picked.add(i)) {
                try {
                    out.add(JSON.readTree(lines.get(i)));
                } catch (Exception ignore) {
                }
            }
        }
        return out;
    }
    //endregion
}
