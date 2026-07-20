package dev.vfyjxf.phonin.search;

/**
 * How a {@link Searcher} matches a query against an indexed name. Determines the {@code partial}
 * flag passed to the matcher: BEGIN/CONTAIN allow a name to satisfy the query without being fully
 * consumed (a partial tail), EQUAL requires the query to consume the name exactly.
 */
public enum SearcherLogic {
    /**
     * The query must match from the start of a name (a prefix), with a partial tail.
     */
    BEGIN(true),

    /**
     * The query may match anywhere within a name (a substring), with a partial tail.
     */
    CONTAIN(true),

    /**
     * The query must consume a whole name exactly.
     */
    EQUAL(false);

    public final boolean partial;

    SearcherLogic(boolean partial) {
        this.partial = partial;
    }
}
