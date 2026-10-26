package io.github.notablogger.springxpose.annotation;

/**
 * Endpoints a class annotated with {@link ExposeEntity} or {@link ExposeDocument} can expose.
 */
public enum Operation {
    /** {@code GET /api/<path>} — every record, paged when {@code pageable = true}. */
    FIND_ALL,
    /** {@code GET /api/<path>/{id}}. */
    FIND_BY_ID,
    /** {@code POST /api/<path>}. */
    CREATE,
    /** {@code PUT /api/<path>/{id}}. */
    UPDATE,
    /** {@code DELETE /api/<path>/{id}}. */
    DELETE,
    /**
     * {@code POST /api/<path>/search} — records matching the filters declared with
     * {@link ExposeField#filterOperations()}, sent as a JSON body. A read: it is secured with the
     * read roles and has no side effects. Not part of the default {@code expose} list.
     */
    SEARCH
}
