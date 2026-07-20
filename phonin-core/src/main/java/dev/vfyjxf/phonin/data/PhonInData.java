package dev.vfyjxf.phonin.data;

import dev.vfyjxf.phonin.Codepoints;
import dev.vfyjxf.phonin.Options;
import dev.vfyjxf.phonin.PhoneticSystem;
import dev.vfyjxf.phonin.model.CharEntry;
import dev.vfyjxf.phonin.model.Reading;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads raw {@code *-char.tsv} data from the classpath into {@link PhoneticSystem} instances. The
 * system objects own their data afterwards, so the matcher never goes through a lookup layer — it
 * reads {@link PhoneticSystem#charEntry(int)} directly.
 *
 * <p>By default nothing is loaded. Call {@link #load(PhoneticSystem...)} or {@link
 * #loadForOptions(Options)} to load the systems you need. Re-loading is a no-op for already-loaded
 * systems.
 */
public final class PhonInData {

    private static final String RAW_BASE = "phonin/raw/";

    private final Set<PhoneticSystem> loaded =
            Collections.newSetFromMap(new ConcurrentHashMap<PhoneticSystem, Boolean>());

    private PhonInData() {}

    /**
     * An empty loader; systems are loaded on demand via {@link #load} or {@link #loadForOptions}.
     */
    public static PhonInData empty() {
        return new PhonInData();
    }

    /**
     * Load the specified systems from the classpath. Already-loaded systems are skipped.
     */
    public synchronized void load(PhoneticSystem... systems) {
        if (systems == null) return;
        for (PhoneticSystem system : systems) {
            if (system == null || loaded.contains(system)) continue;
            loadSystem(system);
            loaded.add(system);
        }
    }

    /**
     * Load the system(s) referenced by {@code options}. Safe to call repeatedly.
     */
    public void loadForOptions(Options options) {
        if (options.system() != null) {
            if (loaded.contains(options.system())) return;
            load(options.system());
        } else if (options.router() != null) {
            List<PhoneticSystem> known = new ArrayList<>();
            for (PhoneticSystem s : options.router().knownSystems()) {
                if (s != null && !loaded.contains(s)) known.add(s);
            }
            if (!known.isEmpty()) {
                load(known.toArray(new PhoneticSystem[0]));
            }
        }
    }

    public boolean isLoaded(PhoneticSystem system) {
        return loaded.contains(system);
    }

    private void loadSystem(PhoneticSystem system) {
        String basePath = RAW_BASE + system.name.toLowerCase();
        String charPath = basePath + "-char.tsv";
        try (InputStream in = openResource(charPath)) {
            if (in == null) return; // system absent from the classpath; do not clear
            system.clearData();
            loadCharTable(system, in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + charPath, e);
        }
        // Word tables (raw/*-word.tsv) are not consumed by the matcher (Phase 2C).
        // Skip them to save startup memory.
    }

    private static void loadCharTable(PhoneticSystem system, InputStream in) throws IOException {
        try (BufferedReader r =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            // Canonicalize repeated normalized/syllable strings within this table to avoid
            // holding thousands of duplicate String objects for common pinyin.
            Map<String, String> stringPool = new HashMap<>(4096);
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] c = line.split("\t", -1);
                if (c.length < 5) continue;
                int cp = Codepoints.parseCodepoint(c[0]);
                if (cp < 0) continue;
                String[] norms = c[3].split(",");
                List<Reading> readings = new ArrayList<>(norms.length);
                for (int i = 0; i < norms.length; i++) {
                    String norm = norms[i].trim();
                    if (norm.isEmpty()) continue;
                    norm = canonical(stringPool, norm);
                    String syllable = system.toneConvention().strip(norm);
                    syllable = canonical(stringPool, syllable);
                    readings.add(new Reading(system, norm, syllable));
                }
                if (!readings.isEmpty()) {
                    system.putChar(new CharEntry(cp, readings));
                }
            }
        }
    }

    private static String canonical(Map<String, String> pool, String s) {
        String existing = pool.putIfAbsent(s, s);
        return existing == null ? s : existing;
    }

    /**
     * Open a classpath resource, trying multiple classloaders. In jar-in-jar environments (NeoForge
     * jarJar, Fabric include) the context class loader may not see nested jars, so we try the
     * class's own loader first, then the context loader, then the system loader.
     */
    private static InputStream openResource(String path) {
        ClassLoader[] loaders = {
            PhonInData.class.getClassLoader(),
            Thread.currentThread().getContextClassLoader(),
            ClassLoader.getSystemClassLoader(),
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            InputStream in = cl.getResourceAsStream(path);
            if (in != null) return in;
        }
        return null;
    }
}
