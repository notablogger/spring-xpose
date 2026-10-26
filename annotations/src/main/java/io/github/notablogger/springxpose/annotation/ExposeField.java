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
 * lets {@code POST /api/products/search} take {@code {"price": 10}}, {@code {"price_gt": 10}} and
 * {@code {"price_lt": 100}}. Filters only take effect when the class exposes
 * {@link Operation#SEARCH}. All filter keys are optional and combined with AND; a key the class does
 * not declare is rejected with {@code 400}.
 * <p>
 * {@code @ExposeField(hidden = true)} keeps a field out of the API: it is left out of the response
 * DTO and the request DTO, but stays on the entity for persistence.
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
 *   <li>JPA relations filter by the related entity's id: to-one ({@code @ManyToOne}, owning
 *       {@code @OneToOne}) supports {@code EQ}, {@code NE}, {@code IN}, {@code NOT_IN}, {@code IS_NULL}
 *       as {@code {"categoryId": 5}}; to-many supports {@code IN}, {@code NOT_IN}, {@code IS_NULL} as
 *       {@code {"suppliers_in": [1, 2]}}. The inverse side of a {@code @OneToOne} cannot be filtered.</li>
 *   <li>Two filters may not map to the same search key or property name.</li>
 *   <li>A {@code hidden} field cannot have {@code filterOperations}: a filter would let clients
 *       guess the value the API hides.</li>
 * </ul>
 * Warned about at compile time: {@code filterOperations} on a class that does not expose
 * {@link Operation#SEARCH} (the filters are unused), and {@code SEARCH} on a class without any
 * {@code filterOperations} (the search returns every record).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExposeField {

    /**
     * Filter operations exposed for this field on the search endpoint
     * ({@link Operation#SEARCH}).
     * Empty (the default) means the field is not filterable.
     */
    FilterOperation[] filterOperations() default {};

    /**
     * When {@code true}, the field is excluded from the generated response DTO and request DTO —
     * clients can neither read nor write it. The entity keeps the field for persistence.
     * Has no effect on the {@code @Id} field, which is always returned.
     * Cannot be combined with {@link #filterOperations()}.
     */
    boolean hidden() default false;
}
