package dev.vfyjxf.phonin.core.match;

import dev.vfyjxf.phonin.core.elements.CharNode;
import dev.vfyjxf.phonin.core.util.IndexSet;

/**
 * The direct matcher, ported from {@code me.towdium.pinin.PinIn.Matcher}. The text is an {@code
 * int[]} of codepoints (so supplementary-plane CJK is handled as single elements); the query is a
 * {@link String} of latin/Bopomofo characters. Each text codepoint's {@link CharNode} reports which
 * query lengths it can consume; {@link Backtrack} chains them.
 */
public final class Matcher {

    public static boolean contains(int[] text, String query, MatchContext ctx) {
        if (query.isEmpty()) return true;
        Cursor src = new Cursor(text, query, true, ctx);
        for (int i = 0; i < text.length; i++) {
            if (Backtrack.run(src, i, query, 0, true)) return true;
        }
        return false;
    }

    public static boolean begins(int[] text, String query, MatchContext ctx) {
        if (text.length == 0) return query.isEmpty();
        return Backtrack.run(new Cursor(text, query, true, ctx), 0, query, 0, true);
    }

    public static boolean matches(int[] text, String query, MatchContext ctx) {
        if (text.length == 0) return query.isEmpty();
        return Backtrack.run(new Cursor(text, query, false, ctx), 0, query, 0, false);
    }

    private static final class Cursor implements Backtrack.Source {
        private final int[] text;
        private final String query;
        private final boolean partial;
        private final MatchContext ctx;

        Cursor(int[] text, String query, boolean partial, MatchContext ctx) {
            this.text = text;
            this.query = query;
            this.partial = partial;
            this.ctx = ctx;
        }

        @Override
        public boolean end(int pos) {
            return pos >= text.length;
        }

        @Override
        public IndexSet matchAt(int pos, int queryOff) {
            return ctx.charNode(text, pos).match(query, queryOff, partial);
        }
    }

    private Matcher() {}
}
