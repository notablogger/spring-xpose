package io.github.notablogger.springxpose.annotation;

/**
 * Comparison operations a field can expose as query filters on the generated
 * {@code GET /api/<path>} (findAll) endpoint. Declared via {@link ExposeField#operations()}.
 * <p>
 * Each operation becomes its own optional query parameter. {@link #EQ} uses the plain
 * field name; every other operation appends a suffix:
 * <pre>
 * EQ   → ?price=10        NE   → ?price_ne=10
 * GT   → ?price_gt=10     GTE  → ?price_gte=10
 * LT   → ?price_lt=10     LTE  → ?price_lte=10
 * LIKE → ?name_like=wid
 * </pre>
 */
public enum FilterOperation {

    /** Equal to the given value. */
    EQ,

    /** Not equal to the given value. Records where the field is {@code null} are included. */
    NE,

    /** Greater than. Field type must be a primitive or {@link Comparable}. */
    GT,

    /** Greater than or equal. Field type must be a primitive or {@link Comparable}. */
    GTE,

    /** Less than. Field type must be a primitive or {@link Comparable}. */
    LT,

    /** Less than or equal. Field type must be a primitive or {@link Comparable}. */
    LTE,

    /**
     * Case-insensitive "contains" match. {@code ?name_like=wid} matches {@code "Widget"}.
     * Wildcard characters in the value are matched literally. Field type must be {@link String}.
     */
    LIKE
}
