package io.phonin.util;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * A compact bitmask of "match lengths" — the set of query-character counts (0..6) by which a single
 * text element can be consumed at a given offset. This is the workhorse of the matcher: {@link
 * io.phonin.elements.Phoneme#match} returns the lengths it can consume, {@link
 * io.phonin.match.Matcher#check} backtracks over those lengths to chain elements.
 *
 * <p>Ported near-verbatim from {@code me.towdium.pinin.utils.IndexSet} (Towdium/PinIn). Bit {@code
 * i} of {@code value} means "a match consuming exactly {@code i} chars is possible here". {@link
 * #zero} (bit 0) is the length-0 anchor that seeds a phoneme chain; {@link #one} (bit 1) is a
 * one-char (literal) match; {@link #none} is no match.
 *
 * <p>The {@link #merge(IndexSet)} replace-when-zero quirk is preserved exactly: merging a result
 * into a set that carries only the length-0 anchor replaces it (so the chain does not accumulate a
 * spurious zero-length option); otherwise results are unioned.
 */
public class IndexSet {
    public static final IndexSet zero = new IndexSet(0x1);
    public static final IndexSet one = new IndexSet(0x2);
    public static final IndexSet none = new IndexSet(0x0);

    int value = 0x0;

    public IndexSet() {}

    public IndexSet(int value) {
        this.value = value;
    }

    /** The raw bitmask; exposed for the hot-path bit loops in {@link io.phonin.match.Matcher}. */
    public int value() {
        return value;
    }

    public void set(int index) {
        value |= (0x1 << index);
    }

    public boolean get(int index) {
        return (value & (0x1 << index)) != 0;
    }

    public void merge(IndexSet s) {
        value = (value == 0x1) ? s.value : (value | s.value);
    }

    public boolean traverse(IntPredicate p) {
        // Iterate every set bit until one satisfies the predicate. Unbounded (any of the 32
        // bits) so a Phoneme whose surface is longer than 6 chars -- e.g. the tone-bearing
        // normalized "zhuang1" (7) -- still works. (PinIn's original capped at 6 because its
        // sub-phonemes were always <= 6 chars; this engine matches whole surfaces.)
        int v = value;
        int i = 0;
        while (v != 0) {
            if ((v & 0x1) == 0x1 && p.test(i)) return true;
            v >>= 1;
            i++;
        }
        return false;
    }

    public void foreach(IntConsumer c) {
        int v = value;
        int i = 0;
        while (v != 0) {
            if ((v & 0x1) == 0x1) c.accept(i);
            v >>= 1;
            i++;
        }
    }

    public boolean isEmpty() {
        return value == 0x0;
    }

    public IndexSet copy() {
        return new IndexSet(value);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        foreach(i -> b.append(i).append(", "));
        if (b.length() != 0) {
            b.delete(b.length() - 2, b.length());
            return b.toString();
        }
        return "0";
    }
}
