package io.github.notablogger.springxpose.annotation;

import java.lang.annotation.*;

/**
 * Field-level configuration for a class annotated with {@link ExposeEntity} or
 * {@link ExposeDocument}.
 * <p>
 * Example:
 * <pre>
 * &#64;ExposeField(filterOperations = {FilterOperation.EQ, FilterOperation.GT, FilterOperation.LT})
 * private BigDecimal price;
 * </pre>
 * generates {@code GET /api/products?price=10}, {@code ?price_gt=10} and {@code ?price_lt=100}.
 * All filter parameters are optional and combined with AND.
 * <p>
 * Checked at compile time:
 * <ul>
 *   <li>{@code GT}, {@code GTE}, {@code LT}, {@code LTE} need a primitive or {@link Comparable} type.</li>
 *   <li>{@code LIKE} needs a {@link String} field.</li>
 *   <li>{@code IS_NULL} cannot be used on primitive fields.</li>
 *   <li>Collection fields support only {@code IN} (has any of), {@code NOT_IN} (has none of) and
 *       {@code IS_NULL} (null or empty); on JPA they must be {@code @ElementCollection} and need a
 *       concrete element type ({@code List<String>}, not a raw {@code List}).</li>
 *   <li>Array fields support only {@code IS_NULL}.</li>
 *   <li>JPA relation fields ({@code @ManyToOne} etc.) cannot be filtered.</li>
 *   <li>Two filters may not map to the same query parameter or property name.</li>
 * </ul>
 * A field listed in {@code ignoredFields} can still be filtered — and its value guessed through
 * the filter — so only declare filters on fields whose values clients may learn.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExposeField {

    /**
     * Filter operations exposed for this field on the findAll endpoint.
     * Empty (the default) means the field is not filterable.
     */
    FilterOperation[] filterOperations() default {};
}
