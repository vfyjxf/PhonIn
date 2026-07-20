package dev.vfyjxf.phonin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, thread-safe table of context-disambiguated polyphone readings — the input standard
 * consumed by {@link PolyphoneMode#PRECISE}. One row per disambiguated word: the word's codepoints
 * and the per-position normalized reading (pinyin + tone digit, e.g. {@code yin2,hang2}) that an
 * external sequence tagger (e.g. MoonBiLSTM) emits. The engine never generates this table; it is
 * loaded from TSV by {@code PolyphoneTables} in {@code phonin-data}, or built programmatically via
 * {@link #builder}, and shared across {@link Options} calls.
 *
 * <p>The table is a codepoint trie keyed by the word's codepoints; each word-terminal node stores
 * the per-position normalized readings. {@link #segment(int[])} walks an input text greedily,
 * taking the longest table word at each position and emitting the forced reading for each covered
 * position (or {@code null} for uncovered positions). This gives O(text-len × max-word-len)
 * longest-match segmentation.
 *
 * <p>TSV format (one row per word; columns tab-separated):
 *
 * <pre>{@code
 * # PhonIn polyphone table — context-disambiguated readings (e.g. MoonBiLSTM-generated).
 * # Columns: codepoints | text | readings_normalized | [source] | [confidence]
 * # (columns are tab-separated; tabs rendered as spaces here)
 * U+94F6,U+884C  银行  yin2,hang2  MOONBILSTM  0.99
 * U+4E00,U+884C  一行  yi1,xing2   MOONBILSTM  0.97
 * }</pre>
 *
 * <p>{@code readings_normalized} is the model's direct output; its count must equal the codepoint
 * count (one reading per char). {@code source}/{@code confidence} are optional trailing columns the
 * engine ignores (kept for provenance / a future confidence threshold). Parsing mirrors {@link
 * PhonInData}'s char-table loader: skip {@code #}/blank lines, {@code split("\t",-1)}, parse {@code
 * U+XXXX} codepoints, split readings on {@code ,}.
 */
public final class PolyphoneTable {

    private final PhoneticSystem system;
    private final Node root;
    private final int maxWordLen; // longest word in the table, in codepoints

    private PolyphoneTable(PhoneticSystem system, Node root, int maxWordLen) {
        this.system = system;
        this.root = root;
        this.maxWordLen = maxWordLen;
    }

    /**
     * The system this table disambiguates readings for.
     */
    public PhoneticSystem system() {
        return system;
    }

    /**
     * The longest word in the table, in codepoints (0 for an empty table).
     */
    public int maxWordLen() {
        return maxWordLen;
    }

    /**
     * Greedy longest-match segmentation of {@code text} against this table.
     *
     * <p>Returns an array of length {@code text.length}; element {@code i} is the forced normalized
     * reading for position {@code i} when some table word covers it, or {@code null} when no word
     * covers it. From each position the longest matching word is taken; segmentation resumes after
     * the whole word (covered positions are not re-anchored inside a matched word).
     *
     * <p>Engine-internal: the engine ({@link dev.vfyjxf.phonin.core.match.MatchContext}) calls this once
     * per match call. Exposed publicly only because the engine lives in a sibling package; callers
     * should not invoke it directly.
     */
    public String[] segment(int[] text) {
        String[] out = new String[text.length];
        int i = 0;
        while (i < text.length) {
            Match m = longestMatch(text, i);
            if (m == null) {
                i++;
                continue;
            }
            for (int k = 0; k < m.wordLen; k++) {
                out[i + k] = m.node.readings[k];
            }
            i += m.wordLen;
        }
        return out;
    }

    /**
     * The longest word starting at {@code from}: its length + the terminal node, or {@code null}.
     */
    private Match longestMatch(int[] text, int from) {
        Node cur = root;
        Node best = null;
        int bestLen = 0;
        int limit = Math.min(text.length, from + maxWordLen);
        for (int i = from; i < limit; i++) {
            Node next = cur.children.get(text[i]);
            if (next == null) break;
            cur = next;
            if (cur.readings != null) {
                best = cur;
                bestLen = i - from + 1;
            }
        }
        return best == null ? null : new Match(bestLen, best);
    }

    private static final class Match {
        final int wordLen;
        final Node node;

        Match(int wordLen, Node node) {
            this.wordLen = wordLen;
            this.node = node;
        }
    }

    //region snapshot of entries

    /**
     * Every word in this table as (codepoints, readings) pairs, in no particular order. Exposed so
     * serialization / conversion utilities (e.g. in {@code phonin-data}) can write the table back
     * without direct access to the trie internals.
     */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        List<Integer> prefix = new ArrayList<>();
        collect(root, prefix, out);
        return out;
    }

    private static void collect(Node node, List<Integer> prefix, List<Entry> out) {
        if (node.readings != null) {
            int[] cps = new int[prefix.size()];
            for (int i = 0; i < prefix.size(); i++) cps[i] = prefix.get(i);
            out.add(new Entry(cps, node.readings));
        }
        for (Map.Entry<Integer, Node> e : node.children.entrySet()) {
            prefix.add(e.getKey());
            collect(e.getValue(), prefix, out);
            prefix.remove(prefix.size() - 1);
        }
    }

    /**
     * One word entry from a {@link PolyphoneTable}.
     */
    public static final class Entry {
        public final int[] codepoints;
        public final String[] readings;

        Entry(int[] codepoints, String[] readings) {
            this.codepoints = codepoints;
            this.readings = readings.clone();
        }
    }

    /**
     * A builder for a {@link PolyphoneTable} (the "Java collection" form).
     */
    public static final class Builder {
        private final PhoneticSystem system;
        private final Node root = new Node();
        private int maxWordLen = 0;

        public Builder(PhoneticSystem system) {
            this.system = Objects.requireNonNull(system, "system");
        }

        /**
         * Add a word by codepoints with its per-position normalized readings.
         */
        public Builder add(int[] codepoints, String... readings) {
            if (codepoints == null || codepoints.length == 0)
                throw new IllegalArgumentException("codepoints required");
            if (readings == null || readings.length != codepoints.length)
                throw new IllegalArgumentException(
                        "readings count must equal codepoint count: "
                                + (readings == null ? 0 : readings.length)
                                + " vs "
                                + codepoints.length);
            Node cur = root;
            for (int cp : codepoints) {
                Node next = cur.children.get(cp);
                if (next == null) {
                    next = new Node();
                    cur.children.put(cp, next);
                }
                cur = next;
            }
            String[] copy = new String[readings.length];
            for (int i = 0; i < readings.length; i++) {
                copy[i] = readings[i].trim();
            }
            cur.readings = copy;
            if (codepoints.length > maxWordLen) maxWordLen = codepoints.length;
            return this;
        }

        /**
         * Convenience: add a word by its {@code String} (converted to codepoints) + readings.
         */
        public Builder add(String word, String... readings) {
            return add(word.codePoints().toArray(), readings);
        }

        public PolyphoneTable build() {
            return new PolyphoneTable(system, root, maxWordLen);
        }
    }

    /**
     * Start a builder for the given system.
     */
    public static Builder builder(PhoneticSystem system) {
        return new Builder(system);
    }

    /**
     * Trie node: children by codepoint, plus per-position readings at a word terminal.
     */
    private static final class Node {
        final Map<Integer, Node> children = new HashMap<>();
        String[] readings = null; // non-null at a word terminal
    }
    //endregion
}
