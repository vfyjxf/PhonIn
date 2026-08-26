package dev.vfyjxf.phonin.core.match;

import dev.vfyjxf.phonin.core.util.IndexSet;

/**
 * The recursive backtracker shared by {@link Matcher} and {@link Accelerator}: at each text
 * position the source supplies the query-lengths the element there can consume (an {@link
 * IndexSet}); recursion advances one text element per consumable length until the query is
 * consumed. On the last text element the match must consume exactly the remaining query.
 */
final class Backtrack {

    /**
     * Supplies per-position match results to the backtracker.
     */
    interface Source {
        /**
         * Position {@code pos} is at/after the end of the text.
         */
        boolean end(int pos);

        /**
         * The query-lengths the text element at {@code pos} can consume at {@code queryOff}.
         */
        IndexSet matchAt(int pos, int queryOff);
    }

    static boolean run(Source src, int pos, String query, int queryOff, boolean partial) {
        if (queryOff == query.length()) return partial || src.end(pos);
        if (src.end(pos)) return false;
        IndexSet s = src.matchAt(pos, queryOff);
        if (src.end(pos + 1)) {
            return s.get(query.length() - queryOff);
        }
        int v = s.value();
        int i = 0;
        while (v != 0) {
            if ((v & 0x1) == 0x1 && run(src, pos + 1, query, queryOff + i, partial)) return true;
            v >>= 1;
            i++;
        }
        return false;
    }

    private Backtrack() {}
}
