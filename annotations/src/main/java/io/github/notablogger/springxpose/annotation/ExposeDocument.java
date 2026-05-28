package io.github.notablogger.springxpose.annotation;

import java.lang.annotation.*;

/**
 * Marks a MongoDB document class for spring-xpose compile-time REST API generation.
 *
 * <p>This is the MongoDB-native counterpart to {@link ExposeEntity} (which is JPA-only).
 * Use {@code @ExposeDocument} on classes annotated with
 * {@code @org.springframework.data.mongodb.core.mapping.Document} — it always
 * targets MongoDB and does not expose JPA-specific options ({@code relationMode}).
 *
 * <p><b>What spring-xpose generates for every {@code @ExposeDocument} class:</b>
 * <ol>
 *   <li>{@code <Entity>Repository}  — extends {@code MongoRepository<Entity, Id>}</li>
 *   <li>{@code <Entity>Dto}         — response DTO (all non-ignored scalar fields + id)</li>
 *   <li>{@code <Entity>RequestDto}  — request body for CREATE/UPDATE (no id, validation annotations copied)</li>
 *   <li>{@code <Entity>Mapper}      — MapStruct mapper interface ({@code toDto}, {@code toDtoList}, {@code toEntity})</li>
 *   <li>{@code <Entity>Controller}  — {@code @RestController} at {@code /api/<path>} — no {@code EntityManager}, no {@code @Transactional}</li>
 *   <li>{@code <Entity>SecurityConfigurer} — {@code SecurityFilterChain} scoped to {@code /api/<path>/**}</li>
 * </ol>
 *
 * <p><b>Minimal example:</b>
 * <pre>{@code
 * @Document(collection = "notes")
 * @ExposeDocument(path = "notes")
 * public class Note {
 *     @Id private String id;
 *     @NotBlank private String title;
 *     private String content;
 * }
 * }</pre>
 *
 * @see ExposeEntity
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExposeDocument {

    /**
     * URL path segment for the generated controller.
     * Defaults to the entity class name lowercased and pluralised
     * (e.g. {@code Note} → {@code "notes"}).
     */
    String path() default "";

    /**
     * HTTP operations to generate. Defaults to all five.
     */
    Operation[] expose() default {
        Operation.FIND_ALL,
        Operation.FIND_BY_ID,
        Operation.CREATE,
        Operation.UPDATE,
        Operation.DELETE
    };

    /** Authentication mechanism for the generated security filter chain. */
    AuthType authType() default AuthType.NONE;

    /** Roles required for all operations (read and write). */
    String[] roles() default {};

    /** Roles for GET requests — overrides {@code roles} for read operations. */
    String[] readRoles() default {};

    /** Roles for POST / PUT / DELETE — overrides {@code roles} for write operations. */
    String[] writeRoles() default {};

    /**
     * Document field names to exclude from both the generated response DTO and request DTO.
     * The document class itself retains these fields for persistence.
     */
    String[] ignoredFields() default {};

    /**
     * When {@code true}, the generated {@code findAll} endpoint accepts Spring Data
     * {@code Pageable} query parameters ({@code ?page=0&size=20&sort=name,asc}) and
     * returns a {@code Page<Dto>} instead of a flat {@code List<Dto>}.
     * <p>
     * Defaults to {@code false}.
     */
    boolean pageable() default false;

    /**
     * Optional custom MapStruct mapper class to use instead of the generated one.
     * <p>
     * When set, spring-xpose omits {@code @Mapper} from the generated interface so
     * MapStruct skips auto-generation. Your class must implement the generated
     * {@code <Entity>Mapper} interface and be annotated with {@code @Component}.
     * <p>
     * Defaults to {@code void.class} — use the MapStruct-generated implementation.
     */
    Class<?> customMapper() default void.class;
}
