package dev.vfyjxf.phonin.core.elements;

import dev.vfyjxf.phonin.core.util.IndexSet;

/**
 * One matchable node in a character's pronunciation lattice. Returns the set of query-char counts
 * (an {@link IndexSet}) it can consume starting at offset {@code start} in the query. If {@code
 * partial} is true the element may also accept a match that ends at the query's end mid-element
 * (prefix / "begins" semantics).
 */
public interface Element {
    IndexSet match(String query, int start, boolean partial);
}
