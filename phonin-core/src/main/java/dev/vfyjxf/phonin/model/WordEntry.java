package dev.vfyjxf.phonin.model;

import dev.vfyjxf.phonin.PhoneticSystem;
import java.util.Collections;
import java.util.List;

/**
 * A multi-character word and its per-syllable {@link Reading}s. Mirrors the Python {@code
 * WordEntry}. Loaded from {@code raw/*-word.tsv}; consumed by word-level matching (Phase 2C) and
 * the {@link dev.vfyjxf.phonin.searchers.Searcher} index (Phase 2G).
 */
public final class WordEntry {
    public final int[] codepoints;
    public final String text;
    public final PhoneticSystem system;
    private final List<Reading> readings;

    public WordEntry(int[] codepoints, String text, PhoneticSystem system, List<Reading> readings) {
        this.codepoints = codepoints;
        this.text = text;
        this.system = system;
        this.readings = readings;
    }

    public List<Reading> readings() {
        return Collections.unmodifiableList(readings);
    }
}
