package dev.vfyjxf.phonin.mandarin;

import dev.vfyjxf.phonin.Keyboard;
import dev.vfyjxf.phonin.core.util.Resources;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link Keyboard} backed by a {@code keymaps/shuangpin-<scheme>.tsv} table (pinyin syllable →
 * 2-key code), itself produced by {@code tools/phonin_data/shuangpin.py} from the RIME
 * double-pinyin algebras (BSD-3-Clause). The Java engine does <em>not</em> port the algebra — it
 * loads the precomputed syllable→code map and looks it up.
 *
 * <p>The keymap is generated from the very same dataset syllables the engine matches, so the lookup
 * key is exactly a {@link dev.vfyjxf.phonin.model.Reading}'s toneless syllable. (Mandarin writes ü
 * as plain {@code u} after {@code j/q/x/y} and as {@code v} after {@code l/n}; the keymap follows
 * the same convention.) Codes may contain non-letter keys — e.g. mspy uses {@code ;} for {@code
 * ing} — and are matched verbatim, char by char.
 *
 * <p>Instances are cached per scheme name; any {@code shuangpin-<name>.tsv} on the classpath is
 * supported, so adding a scheme is a data-only change.
 */
public final class ShuangpinKeyboard implements Keyboard {

    private static final String BASE = "phonin/keymaps/shuangpin-";
    private static final Map<String, ShuangpinKeyboard> cache = new ConcurrentHashMap<>();

    private final String scheme;
    private final Map<String, String> table;

    private ShuangpinKeyboard(String scheme, Map<String, String> table) {
        this.scheme = scheme;
        this.table = table;
    }

    /**
     * Load (and cache) the keyboard for {@code scheme}, e.g. {@code "flypy"}.
     */
    public static ShuangpinKeyboard of(String scheme) {
        Objects.requireNonNull(scheme, "scheme");
        String key = scheme.toLowerCase();
        ShuangpinKeyboard cached = cache.get(key);
        if (cached != null) return cached;
        ShuangpinKeyboard created = new ShuangpinKeyboard(key, load(key));
        ShuangpinKeyboard prior = cache.putIfAbsent(key, created);
        return prior != null ? prior : created;
    }

    @Override
    public boolean isIdentity() {
        return false;
    }

    @Override
    public String encode(String tonelessSyllable) {
        return table.get(tonelessSyllable);
    }

    @Override
    public String normalizeQuery(String query) {
        return Keyboard.normalizeLatin(query);
    }

    public String scheme() {
        return scheme;
    }

    private static Map<String, String> load(String scheme) {
        Map<String, String> table = new HashMap<>();
        String path = BASE + scheme + ".tsv";
        try (InputStream in = Resources.open(ShuangpinKeyboard.class, path)) {
            if (in == null) {
                throw new IllegalStateException("shuangpin keymap not found on classpath: " + path);
            }
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    String[] c = line.split("\t", -1);
                    if (c.length < 2) continue;
                    String syl = c[0].trim();
                    String code = c[1].trim();
                    if (syl.isEmpty() || code.isEmpty()) continue;
                    table.put(syl, code);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load shuangpin keymap: " + scheme, e);
        }
        return Collections.unmodifiableMap(table);
    }

    @Override
    public String toString() {
        return "shuangpin:" + scheme;
    }
}
