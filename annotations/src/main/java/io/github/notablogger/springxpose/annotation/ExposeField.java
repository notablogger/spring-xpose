package io.github.notablogger.springxpose.annotation;

import java.lang.annotation.*;

/**
 * Field-level configuration for a class annotated with {@link ExposeEntity} or
 * {@link ExposeDocument}.
 * <p>
 * Example:
 * <pre>
 * &#64;ExposeField(operations = {FilterOperation.EQ, FilterOperation.GT, FilterOperation.LT})
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
 *   <li>JPA relation fields ({@code @ManyToOne} etc.) cannot be filtered.</li>
 * </ul>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExposeField {

    /**
     * Filter operations exposed for this field on the findAll endpoint.
     * Empty (the default) means the field is not filterable.
     */
    FilterOperation[] operations() default {};
}
