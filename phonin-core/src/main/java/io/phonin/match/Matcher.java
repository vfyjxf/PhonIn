package io.phonin.match;

import io.phonin.elements.CharNode;
import io.phonin.util.IndexSet;

/**
 * The recursive backtracking matcher, ported from {@code me.towdium.pinin.PinIn.Matcher}. The text
 * is an {@code int[]} of codepoints (so supplementary-plane CJK is handled as single elements); the
 * query is a {@link String} of latin/Bopomofo characters.
 *
 * <p>{@link #check} asks the current text codepoint's {@link CharNode} which query lengths it can
 * consume (an {@code IndexSet}); on the last text codepoint the match must consume exactly the
 * remaining query, otherwise it recurses one codepoint deeper for each consumable length.
 */
public final class Matcher {

    public static boolean contains(int[] text, String query, MatchContext ctx) {
        if (query.isEmpty()) return true;
        for (int i = 0; i < text.length; i++) {
            if (check(text, i, query, 0, ctx, true)) return true;
        }
        return false;
    }

    public static boolean begins(int[] text, String query, MatchContext ctx) {
        if (text.length == 0) return query.isEmpty();
        return check(text, 0, query, 0, ctx, true);
    }

    public static boolean matches(int[] text, String query, MatchContext ctx) {
        if (text.length == 0) return query.isEmpty();
        return check(text, 0, query, 0, ctx, false);
    }

    static boolean check(
            int[] text, int start1, String query, int start2, MatchContext ctx, boolean partial) {
        if (start2 == query.length()) return partial || start1 == text.length;

        CharNode node = ctx.charNode(text, start1);
        IndexSet s = node.match(query, start2, partial);

        if (start1 == text.length - 1) {
            int need = query.length() - start2;
            return s.get(need);
        }
        // Inline IndexSet bit traversal to avoid per-step IntPredicate lambda allocation.
        int v = s.value();
        int i = 0;
        while (v != 0) {
            if ((v & 0x1) == 0x1 && check(text, start1 + 1, query, start2 + i, ctx, partial))
                return true;
            v >>= 1;
            i++;
        }
        return false;
    }

    private Matcher() {}
}
