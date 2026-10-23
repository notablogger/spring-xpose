package io.github.notablogger.springxpose.annotation;

/**
 * Comparison operations a field can expose as query filters on the generated
 * {@code GET /api/<path>} (findAll) endpoint. Declared via {@link ExposeField#filterOperations()}.
 * <p>
 * Each operation becomes its own optional query parameter. {@link #EQ} uses the plain
 * field name; every other operation appends a suffix:
 * <pre>
 * EQ     → ?price=10              NE      → ?price_ne=10
 * GT     → ?price_gt=10           GTE     → ?price_gte=10
 * LT     → ?price_lt=10           LTE     → ?price_lte=10
 * LIKE   → ?name_like=wid
 * IN     → ?status_in=A,B         NOT_IN  → ?status_not_in=A,B
 * IS_NULL → ?deletedAt_is_null=true
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
    LIKE,

    /**
     * Equal to any of the given values: {@code ?status_in=ACTIVE,PENDING} (or the parameter repeated).
     * On a collection field: has any of the values. An empty list adds no condition.
     */
    IN,

    /**
     * Equal to none of the given values: {@code ?status_not_in=DELETED,ARCHIVED}.
     * Records where the field is {@code null} are included, as with {@link #NE}.
     * On a collection field: has none of the values (empty collections match).
     * An empty list adds no condition.
     */
    NOT_IN,

    /**
     * {@code ?deletedAt_is_null=true} matches records where the field is {@code null};
     * {@code false} matches records where it is set. Not supported on primitive fields.
     * On a collection field: {@code true} matches null or empty collections.
     */
    IS_NULL
}
