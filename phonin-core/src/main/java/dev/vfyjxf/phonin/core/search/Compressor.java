package dev.vfyjxf.phonin.core.search;

import dev.vfyjxf.phonin.core.match.Accelerator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * A pool of all indexed names' codepoints, concatenated with a {@code 0} sentinel between them.
 * Serves as the {@link Accelerator.Provider} for {@link TreeSearcher}'s trie (names are referenced
 * by their start offset into the pool, giving one address space across all names). Ported from
 * {@code me.towdium.pinin.utils.Compressor}, changed from {@code char} to {@code int} codepoints so
 * supplementary-plane CJK is handled as single elements.
 *
 * <p>The sentinel reserves the NUL codepoint (U+0000): a name containing it would be split at the
 * embedded NUL and silently mis-match, so {@link #put(String)} rejects such names loudly rather
 * than corrupting the trie.
 */
final class Compressor implements Accelerator.Provider {

    private final IntList chars = new IntArrayList();

    /**
     * Append a name; return its start offset in the pool.
     */
    int put(String name) {
        if (name.indexOf(0) >= 0) {
            throw new IllegalArgumentException(
                    "indexed name contains NUL (U+0000), which is reserved as the name sentinel");
        }
        int start = chars.size();
        name.codePoints().forEach(chars::add);
        chars.add(0); // sentinel
        return start;
    }

    @Override
    public boolean end(int i) {
        return i >= chars.size() || chars.getInt(i) == 0;
    }

    @Override
    public int codepoint(int i) {
        return chars.getInt(i);
    }

    /**
     * Pool size including sentinels; a name put at offset {@code start} spans {@code size() -
     * start - 1} codepoints.
     */
    int size() {
        return chars.size();
    }
}
