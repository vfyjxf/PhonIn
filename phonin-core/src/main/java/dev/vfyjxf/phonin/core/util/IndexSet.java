package dev.vfyjxf.phonin.core.util;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * A compact bitmask of "match lengths" — the set of query-character counts (0..31) by which a
 * single text element can be consumed at a given offset. This is the workhorse of the matcher:
 * {@link dev.vfyjxf.phonin.core.elements.Phoneme#match} returns the lengths it can consume, and the
 * backtracker recurses over those lengths to chain elements.
 *
 * <p>Ported near-verbatim from {@code me.towdium.pinin.utils.IndexSet} (Towdium/PinIn). Bit {@code
 * i} of {@code value} means "a match consuming exactly {@code i} chars is possible here".
 *
 * <p>The {@link #merge(IndexSet)} replace-when-zero quirk is preserved exactly: merging a result
 * into a set that carries only the length-0 anchor replaces it (so the chain does not accumulate a
 * spurious zero-length option); otherwise results are unioned.
 *
 * <p>Mutable and not thread-safe; do not share instances.
 */
public class IndexSet {

    int value = 0x0;

    public IndexSet() {}

    public IndexSet(int value) {
        this.value = value;
    }

    /**
     * A fresh {0} set — the length-0 anchor that seeds a phoneme chain.
     */
    public static IndexSet zero() {
        return new IndexSet(0x1);
    }

    /**
     * A fresh {1} set — a one-char (literal) match.
     */
    public static IndexSet one() {
        return new IndexSet(0x2);
    }

    /**
     * A fresh empty set — no match.
     */
    public static IndexSet none() {
        return new IndexSet(0x0);
    }

    /**
     * The raw bitmask; exposed for the hot-path bit loops in {@link
     * dev.vfyjxf.phonin.core.match.Matcher}.
     */
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
